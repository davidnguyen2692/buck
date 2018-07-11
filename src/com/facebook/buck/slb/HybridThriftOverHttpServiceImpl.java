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

package com.facebook.buck.slb;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import okhttp3.MediaType;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.apache.thrift.TBase;

/**
 * The HTTP body contains: - 4 bytes big endian byte size of the thrift serialised message. - N
 * bytes of the thrift serialised message. - Remaining bytes correspond to the out-of-band
 * payload(s).
 */
public class HybridThriftOverHttpServiceImpl<
        ThriftRequest extends TBase<?, ?>, ThriftResponse extends TBase<?, ?>>
    implements HybridThriftOverHttpService<ThriftRequest, ThriftResponse> {

  public static final MediaType HYBRID_THRIFT_STREAM_CONTENT_TYPE =
      MediaType.parse("application/x-hybrid-thrift-binary");
  public static final String PROTOCOL_HEADER = "X-Thrift-Protocol";

  private final HybridThriftOverHttpServiceArgs args;

  /** New instances. */
  public HybridThriftOverHttpServiceImpl(HybridThriftOverHttpServiceArgs args) {
    this.args = args;
  }

  /** @inheritDoc */
  @Override
  public ListenableFuture<ThriftResponse> makeRequest(
      HybridThriftRequestHandler<ThriftRequest> request,
      HybridThriftResponseHandler<ThriftResponse> responseHandler) {
    final SettableFuture<ThriftResponse> future = SettableFuture.create();
    args.getExecutor().submit(() -> future.set(makeRequestSync(request, responseHandler)));
    return future;
  }

  /** @inheritDoc */
  @Override
  public ThriftResponse makeRequestSync(
      HybridThriftRequestHandler<ThriftRequest> request,
      HybridThriftResponseHandler<ThriftResponse> responseHandler)
      throws IOException {
    byte[] serializedThriftData =
        ThriftUtil.serialize(args.getThriftProtocol(), request.getRequest());
    long totalRequestSizeBytes =
        4 + serializedThriftData.length + request.getTotalPayloadsSizeBytes();
    Builder builder =
        new Builder().addHeader(PROTOCOL_HEADER, args.getThriftProtocol().toString().toLowerCase());
    builder.post(
        new RequestBody() {
          @Override
          public MediaType contentType() {
            return HYBRID_THRIFT_STREAM_CONTENT_TYPE;
          }

          @Override
          public long contentLength() {
            return totalRequestSizeBytes;
          }

          @Override
          public void writeTo(BufferedSink bufferedSink) throws IOException {
            try (DataOutputStream outputStream =
                new DataOutputStream(bufferedSink.outputStream())) {
              writeToStream(outputStream, serializedThriftData, request);
            }
          }
        });

    HttpResponse response = args.getService().makeRequest(args.getHybridThriftPath(), builder);
    try (DataInputStream bodyStream = new DataInputStream(response.getBody())) {
      return readFromStream(bodyStream, args.getThriftProtocol(), responseHandler);
    }
  }

  @VisibleForTesting
  static <ThriftRequest extends TBase<?, ?>> void writeToStream(
      DataOutputStream outputStream,
      byte[] serializedThriftData,
      HybridThriftRequestHandler<ThriftRequest> request)
      throws IOException {
    outputStream.writeInt(serializedThriftData.length);
    outputStream.write(serializedThriftData);
    for (int i = 0; i < request.getNumberOfPayloads(); ++i) {
      try (InputStream inputStream = request.getPayloadStream(i)) {
        ByteStreams.copy(inputStream, outputStream);
      }
    }
  }

  @VisibleForTesting
  static <ThriftResponse extends TBase<?, ?>> ThriftResponse readFromStream(
      DataInputStream bodyStream,
      ThriftProtocol protocol,
      HybridThriftResponseHandler<ThriftResponse> responseHandler)
      throws IOException {

    ThriftResponse thriftResponse = responseHandler.getThriftResponse();
    int thriftDataSizeBytes = bodyStream.readInt();
    Preconditions.checkState(
        thriftDataSizeBytes >= 0,
        "Field thriftDataSizeBytes must be non-negative. Found [%d].",
        thriftDataSizeBytes);
    ThriftUtil.deserialize(
        protocol, ByteStreams.limit(bodyStream, thriftDataSizeBytes), thriftResponse);
    int payloadCount = responseHandler.getTotalPayloads();
    for (int i = 0; i < payloadCount; ++i) {
      long payloadSizeBytes = responseHandler.getPayloadSizeBytes(i);
      try (OutputStream outStream = responseHandler.getStreamForPayload(i)) {
        ByteStreams.copy(ByteStreams.limit(bodyStream, payloadSizeBytes), outStream);
      }
    }

    return thriftResponse;
  }
}
