/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.rage;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.log.InvocationInfo;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.immutables.value.Value;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Methods for finding and inspecting buck log files.
 *
 */
public class BuildLogHelper {
  // Max number of lines to read from a build.log file when searching for 'header' entries like
  // "started", "system properties", etc...
  private static final int MAX_LINES_TO_SCAN_FOR_LOG_HEADER = 100;
  private static final String BUCK_LOG_FILE = BuckConstant.BUCK_LOG_FILE_NAME;

  private final ProjectFilesystem projectFilesystem;
  private final ObjectMapper objectMapper;

  public BuildLogHelper(
      ProjectFilesystem projectFilesystem,
      ObjectMapper objectMapper) {
    this.projectFilesystem = projectFilesystem;
    this.objectMapper = objectMapper;
  }

  public ImmutableList<BuildLogEntry> getBuildLogs() throws IOException {
    Collection<Path> logFiles = getAllBuckLogFiles();
    ImmutableList.Builder<BuildLogEntry> logEntries = ImmutableList.builder();
    for (Path logFile : logFiles) {
      logEntries.add(newBuildLogEntry(logFile));
    }
    return logEntries.build();
  }

  private BuildLogEntry newBuildLogEntry(Path logFile) throws IOException {
    Optional<InvocationInfo.ParsedLog> parsedLine = extractFirstMatchingLine(logFile);
    BuildLogEntry.Builder builder = BuildLogEntry.builder();

    if (parsedLine.isPresent()) {
      builder.setBuildId(parsedLine.get().getBuildId());
      builder.setCommandArgs(parsedLine.get().getArgs());
    }

    Path ruleKeyLoggerFile = logFile.getParent().resolve(BuckConstant.RULE_KEY_LOGGER_FILE_NAME);
    if (projectFilesystem.isFile(ruleKeyLoggerFile)) {
      builder.setRuleKeyLoggerLogFile(ruleKeyLoggerFile);
    }

    Path machineReadableLogFile =
        logFile.getParent().resolve(BuckConstant.BUCK_MACHINE_LOG_FILE_NAME);
    if (projectFilesystem.isFile(machineReadableLogFile)) {
      builder.setMachineReadableLogFile(machineReadableLogFile);
      builder.setExitCode(readExitCode(machineReadableLogFile));

    }

    Optional <Path> traceFile =
        projectFilesystem.getFilesUnderPath(logFile.getParent()).stream()
            .filter(input -> input.toString().endsWith(".trace"))
            .findFirst();

    return builder
        .setRelativePath(logFile)
        .setSize(projectFilesystem.getFileSize(logFile))
        .setLastModifiedTime(new Date(projectFilesystem.getLastModifiedTime(logFile)))
        .setTraceFile(traceFile)
        .build();
  }

  private Optional<InvocationInfo.ParsedLog> extractFirstMatchingLine(Path logPath)
      throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(projectFilesystem.resolve(logPath))) {
      for (int i = 0; i < MAX_LINES_TO_SCAN_FOR_LOG_HEADER; ++i) {
        String line = reader.readLine();
        if (line == null) { // EOF.
          break;
        }

        Optional<InvocationInfo.ParsedLog> result = InvocationInfo.parseLogLine(line);
        if (result.isPresent()) {
          return result;
        }
      }
    }

    return Optional.empty();
  }

  private OptionalInt readExitCode(Path machineReadableLogFile) {
    try (BufferedReader reader = Files.newBufferedReader(
        projectFilesystem.resolve(machineReadableLogFile))) {
      List<String> lines = reader
          .lines()
          .filter(s -> s.startsWith("ExitCode"))
          .collect(Collectors.toList());

      if (lines.size() == 1) {
        Map<String, Integer> exitCode =
            objectMapper.readValue(
                lines.get(0).substring("ExitCode ".length()).getBytes(Charsets.UTF_8),
                new TypeReference<Map<String, Integer>>(){});
        if (exitCode.containsKey("exitCode")) {
          return OptionalInt.of(exitCode.get("exitCode"));
        }
      }
    } catch (IOException e) {
      return OptionalInt.empty();
    }
    return OptionalInt.empty();
  }

  public Collection<Path> getAllBuckLogFiles() throws IOException {
    final List<Path> logfiles = Lists.newArrayList();
    projectFilesystem.walkRelativeFileTree(
        projectFilesystem.getBuckPaths().getLogDir(),
        new FileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(
          Path dir, BasicFileAttributes attrs) throws IOException {
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(
          Path file, BasicFileAttributes attrs) throws IOException {
        if (file.getFileName().toString().equals(BUCK_LOG_FILE)) {
          logfiles.add(file);
        }

        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        return FileVisitResult.CONTINUE;
      }
    });

    return logfiles;
  }

  @Value.Immutable
  @BuckStyleImmutable
  abstract static class AbstractBuildLogEntry {
    public abstract Path getRelativePath();
    public abstract Optional<BuildId> getBuildId();
    public abstract Optional<String> getCommandArgs();
    public abstract OptionalInt getExitCode();
    public abstract Optional<Path> getRuleKeyLoggerLogFile();
    public abstract Optional<Path> getMachineReadableLogFile();
    public abstract Optional<Path> getTraceFile();
    public abstract long getSize();
    public abstract Date getLastModifiedTime();

    @Value.Check
    void pathIsRelative() {
      Preconditions.checkState(!getRelativePath().isAbsolute());
      if (getRuleKeyLoggerLogFile().isPresent()) {
        Preconditions.checkState(!getRuleKeyLoggerLogFile().get().isAbsolute());
      }
      if (getTraceFile().isPresent()) {
        Preconditions.checkState(!getTraceFile().get().isAbsolute());
      }
    }
  }
}
