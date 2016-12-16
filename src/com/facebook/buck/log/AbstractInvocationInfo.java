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

package com.facebook.buck.log;

import com.facebook.buck.log.views.JsonViews;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Joiner;

import org.immutables.value.Value;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Value.Immutable
@BuckStyleImmutable
@JsonDeserialize(as = InvocationInfo.class)
abstract class AbstractInvocationInfo {
  public static final SimpleDateFormat DIR_DATE_FORMAT;
  public static final String DIR_NAME_REGEX = ".+_.+_.+";
  private static final String DIR_NAME_TEMPLATE = "%s_%s_%s";

  static {
    DIR_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH'h'mm'm'ss's'");
    DIR_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  // TODO(#13704826): we should switch over to a machine-readable log format.
  private static final String LOG_MSG_TEMPLATE = "InvocationInfo BuildId=[%s] Args=[%s]";
  private static final Pattern LOG_MSG_PATTERN = Pattern.compile(
      "InvocationInfo BuildId=\\[(?<buildid>.+)\\] Args=\\[(?<args>.+)\\]");

  @Value.Parameter
  @JsonView(JsonViews.MachineReadableLog.class)
  public abstract BuildId getBuildId();

  @Value.Parameter
  @JsonView(JsonViews.MachineReadableLog.class)
  public abstract boolean getSuperConsoleEnabled();

  @Value.Parameter
  @JsonView(JsonViews.MachineReadableLog.class)
  public abstract boolean getIsDaemon();

  @Value.Parameter
  @JsonView(JsonViews.MachineReadableLog.class)
  public abstract String getSubCommand();

  @Value.Parameter
  public abstract Path getBuckLogDir();

  @Value.Default
  @JsonView(JsonViews.MachineReadableLog.class)
  public long getTimestampMillis() {
    return System.currentTimeMillis();
  }

  // Just a convenient explicit alias.
  public String getCommandId() {
    return getBuildId().toString();
  }

  public String getLogDirectoryName() {
    return String.format(
        DIR_NAME_TEMPLATE,
        DIR_DATE_FORMAT.format(getTimestampMillis()),
        getSubCommand(),
        getBuildId());
  }

  public String toLogLine(String[] args) {
    return String.format(LOG_MSG_TEMPLATE, getBuildId().toString(), Joiner.on(", ").join(args));
  }

  public static Optional<ParsedLog> parseLogLine(String line) {
    Matcher matcher = LOG_MSG_PATTERN.matcher(line);
    if (matcher.find()) {
      BuildId buildId = new BuildId(matcher.group("buildid"));
      String args = matcher.group("args");
      return Optional.of(new ParsedLog(buildId, args));
    }

    return Optional.empty();
  }

  public Path getLogDirectoryPath() {
    return getBuckLogDir().resolve(getLogDirectoryName() + "/");
  }

  public Path getLogFilePath() {
    return getLogDirectoryPath().resolve(BuckConstant.BUCK_LOG_FILE_NAME);
  }

  @Override
  public String toString() {
    return String.format("buildId=[%s] subCommand=[%s] utcMillis=[%d]",
        getBuildId().toString(),
        getSubCommand(),
        getTimestampMillis());
  }

  public static class ParsedLog {
    private final BuildId buildId;
    private final String args;

    public ParsedLog(BuildId buildId, String args) {
      this.buildId = buildId;
      this.args = args;
    }

    public BuildId getBuildId() {
      return buildId;
    }

    public String getArgs() {
      return args;
    }
  }
}
