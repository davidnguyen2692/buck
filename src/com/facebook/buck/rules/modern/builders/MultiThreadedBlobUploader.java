/*
 * Copyright 2018-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.rules.modern.builders;

import com.facebook.buck.rules.modern.builders.Protocol.Digest;
import com.facebook.buck.util.concurrent.MoreFutures;
import com.facebook.buck.util.function.ThrowingSupplier;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * A simple multi-threaded blob uploader for uploading inputs/outputs to the CAS.
 *
 * <p>Before uploading a file, the uploader will check if the CAS already contains it.
 *
 * <p>All upload requests get added to a queue for the "missing check". Work threads will pull up to
 * missingCheckLimit items off this queue and send a request to the CAS to find which it
 * does/doesn't contain. Any that are missing will be added to a queue to be uploaded. Work threads
 * will then pull those off and upload them. When the upload is finished, the future for that digest
 * will be fulfilled.
 */
public class MultiThreadedBlobUploader {
  private final int missingCheckLimit;
  private final int uploadSizeLimit;

  public MultiThreadedBlobUploader(
      int missingCheckLimit,
      int uploadSizeLimit,
      ExecutorService uploadService,
      CasBlobUploader delegate) {
    this.missingCheckLimit = missingCheckLimit;
    this.uploadSizeLimit = uploadSizeLimit;
    this.uploadService = uploadService;
    this.asyncBlobUploader = delegate;
  }

  private final ConcurrentHashMap<String, ListenableFuture<Void>> pendingUploads =
      new ConcurrentHashMap<>();

  private final Set<String> containedHashes = Sets.newConcurrentHashSet();
  private final BlockingQueue<PendingUpload> waitingUploads = new LinkedBlockingQueue<>();
  private final BlockingQueue<PendingUpload> waitingMissingCheck = new LinkedBlockingQueue<>();

  private final ExecutorService uploadService;
  private final CasBlobUploader asyncBlobUploader;

  /**
   * Data required to upload a file. The underlying data will only be read if the CAS is missing
   * this digest.
   */
  public static class UploadData {
    public final Protocol.Digest digest;
    public final ThrowingSupplier<InputStream, IOException> data;

    public UploadData(Protocol.Digest digest, ThrowingSupplier<InputStream, IOException> data) {
      this.digest = digest;
      this.data = data;
    }

    public String getHash() {
      return digest.getHash();
    }
  }

  private static class PendingUpload {
    private final UploadData uploadData;
    private final SettableFuture<Void> future;

    PendingUpload(UploadData uploadData, SettableFuture<Void> future) {
      this.uploadData = uploadData;
      this.future = future;
    }

    String getHash() {
      return uploadData.getHash();
    }
  }

  /** Uploads missing items to the CAS. */
  public void addMissing(
      ImmutableMap<Protocol.Digest, ThrowingSupplier<InputStream, IOException>> data)
      throws IOException {
    try {
      data =
          ImmutableMap.copyOf(Maps.filterKeys(data, k -> !containedHashes.contains(k.getHash())));
      if (data.isEmpty()) {
        return;
      }
      enqueue(data).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException(e);
    }
  }

  private ListenableFuture<Void> enqueue(
      ImmutableMap<Protocol.Digest, ThrowingSupplier<InputStream, IOException>> data) {
    ImmutableList.Builder<ListenableFuture<Void>> futures = ImmutableList.builder();
    for (Entry<Protocol.Digest, ThrowingSupplier<InputStream, IOException>> entry :
        data.entrySet()) {
      Protocol.Digest digest = entry.getKey();
      ListenableFuture<Void> resultFuture =
          pendingUploads.computeIfAbsent(
              digest.getHash(),
              hash -> {
                if (containedHashes.contains(hash)) {
                  return Futures.immediateFuture(null);
                }
                SettableFuture<Void> future = SettableFuture.create();
                waitingMissingCheck.add(
                    new PendingUpload(new UploadData(digest, entry.getValue()), future));
                return future;
              });
      Futures.addCallback(
          resultFuture,
          MoreFutures.finallyCallback(
              () -> {
                containedHashes.add(digest.getHash());
                pendingUploads.remove(digest.getHash());
              }));
      futures.add(resultFuture);
      uploadService.submit(this::processUploads);
    }
    return Futures.whenAllSucceed(futures.build()).call(() -> null);
  }

  private void processMissing() {
    ImmutableList.Builder<PendingUpload> dataBuilder = ImmutableList.builder();
    int count = 0;
    while (count < missingCheckLimit && !waitingMissingCheck.isEmpty()) {
      PendingUpload data = waitingMissingCheck.poll();
      if (data == null) {
        break;
      }
      dataBuilder.add(data);
      count++;
    }

    if (count == 0) {
      return;
    }

    ImmutableList<PendingUpload> data = dataBuilder.build();

    try {
      List<Protocol.Digest> requiredDigests =
          data.stream().map(entry -> entry.uploadData.digest).collect(Collectors.toList());

      Set<String> missing = asyncBlobUploader.getMissingHashes(requiredDigests);

      for (PendingUpload entry : data) {
        if (missing.contains(entry.getHash())) {
          waitingUploads.add(entry);
        } else {
          entry.future.set(null);
        }
      }
    } catch (Throwable e) {
      data.forEach(d -> d.future.setException(e));
    }
  }

  private void processUploads() {
    processMissing();
    ImmutableMap.Builder<String, PendingUpload> dataBuilder = ImmutableMap.builder();
    int size = 0;
    while (size < uploadSizeLimit && !waitingUploads.isEmpty()) {
      PendingUpload data = waitingUploads.poll();
      if (data == null) {
        break;
      }
      dataBuilder.put(data.getHash(), data);
      size += data.uploadData.digest.getSize();
    }
    ImmutableMap<String, PendingUpload> data = dataBuilder.build();

    if (!data.isEmpty()) {
      try {
        ImmutableList.Builder<UploadData> blobsBuilder = ImmutableList.builder();
        for (PendingUpload entry : data.values()) {
          blobsBuilder.add(entry.uploadData);
        }

        ImmutableList<UploadData> blobs =
            data.values().stream().map(e -> e.uploadData).collect(ImmutableList.toImmutableList());

        ImmutableList<UploadResult> results = asyncBlobUploader.batchUpdateBlobs(blobs);
        Preconditions.checkState(results.size() == blobs.size());
        results.forEach(
            result -> {
              PendingUpload pendingUpload =
                  Preconditions.checkNotNull(data.get(result.digest.getHash()));
              if (result.status == 0) {
                pendingUpload.future.set(null);
              } else {
                pendingUpload.future.setException(
                    new IOException(
                        String.format("Failed uploading with message: %s", result.message)));
              }
            });
        data.forEach((k, pending) -> pending.future.setException(new RuntimeException("idk")));
      } catch (Exception e) {
        data.forEach((k, pending) -> pending.future.setException(e));
      }
    }
    if (!waitingMissingCheck.isEmpty() || !waitingUploads.isEmpty()) {
      uploadService.submit(this::processUploads);
    }
  }

  /** Result (status/error message) of an upload. */
  public static class UploadResult {
    public final Digest digest;
    public final int status;
    @Nullable public final String message;

    public UploadResult(Digest digest, int status, @Nullable String message) {
      this.digest = digest;
      this.status = status;
      this.message = message;
    }
  }
}
