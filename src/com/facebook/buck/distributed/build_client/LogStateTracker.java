/*
 * Copyright 2017-present Facebook, Inc.
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
package com.facebook.buck.distributed.build_client;

import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.distributed.DistBuildService;
import com.facebook.buck.distributed.DistBuildUtil;
import com.facebook.buck.distributed.thrift.BuildSlaveInfo;
import com.facebook.buck.distributed.thrift.LogLineBatch;
import com.facebook.buck.distributed.thrift.LogLineBatchRequest;
import com.facebook.buck.distributed.thrift.LogStreamType;
import com.facebook.buck.distributed.thrift.SlaveStream;
import com.facebook.buck.distributed.thrift.StreamLogs;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Tracks the state of logs. */
public class LogStateTracker {
  private static final Logger LOG = Logger.get(LogStateTracker.class);
  private static final List<LogStreamType> SUPPORTED_STREAM_TYPES =
      ImmutableList.of(LogStreamType.STDOUT, LogStreamType.STDERR);

  private final BuildSlaveLogsMaterializer materializer;
  private final Path logDirectoryPath;
  private final ProjectFilesystem filesystem;

  private Map<SlaveStream, SlaveStreamState> seenSlaveLogs = new HashMap<>();
  private Set<String> createdLogDirRootsByRunId = new HashSet<>();

  public LogStateTracker(
      Path logDirectoryPath, ProjectFilesystem filesystem, DistBuildService service) {
    this.logDirectoryPath = logDirectoryPath;
    this.filesystem = filesystem;
    this.materializer = new BuildSlaveLogsMaterializer(service, filesystem, logDirectoryPath);
  }

  /** Creates requests for the next set of StreamLogs. */
  public List<LogLineBatchRequest> createStreamLogRequests(
      Collection<BuildSlaveInfo> latestBuildSlaveInfos) {
    List<LogLineBatchRequest> requests = new ArrayList<>();
    for (LogStreamType streamType : SUPPORTED_STREAM_TYPES) {
      for (BuildSlaveInfo buildSlaveInfo : latestBuildSlaveInfos) {
        SlaveStream slaveStream =
            new SlaveStream()
                .setBuildSlaveRunId(buildSlaveInfo.buildSlaveRunId)
                .setStreamType(streamType);
        int lastBatchNumber = getLatestBatchNumber(slaveStream);
        requests.add(createRequest(slaveStream, lastBatchNumber));
      }
    }
    return requests;
  }

  public void processStreamLogs(List<StreamLogs> multiStreamLogs) {
    for (StreamLogs streamLogs : multiStreamLogs) {
      if (streamLogs.isSetErrorMessage()) {
        LOG.error(
            "Failed to get stream logs for runId [%s]. Error: [%s].",
            streamLogs.slaveStream.buildSlaveRunId, streamLogs.errorMessage);

        continue;
      }

      processStreamLogs(streamLogs);
    }
  }

  public BuildSlaveLogsMaterializer getBuildSlaveLogsMaterializer() {
    return materializer;
  }

  /*
   *******************************
   *  Helpers
   *******************************
   */

  private void processStreamLogs(StreamLogs streamLogs) {
    if (streamLogs.logLineBatches.isEmpty()) {
      // No new lines have been logged.
      return;
    }

    if (!seenSlaveLogs.containsKey(streamLogs.slaveStream)) {
      seenSlaveLogs.put(streamLogs.slaveStream, new SlaveStreamState());
    }

    SlaveStreamState seenStreamState =
        Objects.requireNonNull(seenSlaveLogs.get(streamLogs.slaveStream));

    LogLineBatch lastReceivedBatch =
        streamLogs.logLineBatches.get(streamLogs.logLineBatches.size() - 1);

    if (seenStreamState.seenBatchNumber > lastReceivedBatch.batchNumber
        || (seenStreamState.seenBatchNumber == lastReceivedBatch.batchNumber
            && seenStreamState.seenBatchLineCount >= lastReceivedBatch.lines.size())) {
      LOG.warn(
          "Received stale logs for runID [%s] and stream [%s]",
          streamLogs.slaveStream.buildSlaveRunId, streamLogs.slaveStream.streamType);
      return;
    }

    // Determines which log lines need writing, and then writes them to disk.
    List<String> newLines = new ArrayList<>();
    for (LogLineBatch batch : streamLogs.logLineBatches) {
      if (batch.batchNumber < seenStreamState.seenBatchNumber) {
        continue;
      }

      if (batch.batchNumber == seenStreamState.seenBatchNumber) {
        if (batch.lines.size() == seenStreamState.seenBatchLineCount) {
          continue;
        }
        newLines.addAll(
            batch.lines.subList(seenStreamState.seenBatchLineCount, batch.lines.size()));
      } else {
        newLines.addAll(batch.lines);
      }
    }

    writeLogStreamLinesToDisk(streamLogs.slaveStream, newLines);

    seenStreamState.seenBatchNumber = lastReceivedBatch.batchNumber;
    seenStreamState.seenBatchLineCount = lastReceivedBatch.lines.size();
  }

  private int getLatestBatchNumber(SlaveStream stream) {
    if (seenSlaveLogs.containsKey(stream)) {
      return seenSlaveLogs.get(stream).seenBatchNumber;
    }

    return 0;
  }

  private static LogLineBatchRequest createRequest(SlaveStream slaveStream, int batchNumber) {
    LogLineBatchRequest request = new LogLineBatchRequest();
    request.setSlaveStream(slaveStream);
    request.setBatchNumber(batchNumber);
    return request;
  }

  /*
   *******************************
   *  Path utils
   *******************************
   */

  private void createLogDir(String runId, Path logDir) {
    if (!createdLogDirRootsByRunId.contains(runId)) {
      try {
        filesystem.mkdirs(logDir);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      createdLogDirRootsByRunId.add(runId);
    }
  }

  private Path getStreamLogFilePath(String runId, String streamType) {
    Path filePath = DistBuildUtil.getStreamLogFilePath(runId, streamType, logDirectoryPath);
    createLogDir(runId, filePath.getParent());
    return filePath;
  }

  /*
   *******************************
   *  Streaming log materialization
   *******************************
   */

  private void writeLogStreamLinesToDisk(SlaveStream slaveStream, List<String> newLines) {
    Path outputLogFilePath =
        getStreamLogFilePath(slaveStream.buildSlaveRunId.id, slaveStream.streamType.toString());
    try (OutputStream outputStream =
        new BufferedOutputStream(new FileOutputStream(outputLogFilePath.toFile(), true))) {
      for (String logLine : newLines) {
        outputStream.write(logLine.getBytes(Charsets.UTF_8));
      }
      outputStream.flush();
    } catch (IOException e) {
      LOG.debug("Failed to write to %s", outputLogFilePath.toAbsolutePath(), e);
    }
  }

  /*
   *******************************
   *  Inner classes
   *******************************
   */

  private static class SlaveStreamState {
    private int seenBatchNumber;
    private int seenBatchLineCount;
  }
}
