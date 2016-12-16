/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.cli;

import com.facebook.buck.config.CellConfig;
import com.facebook.buck.event.BuckEventListener;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.log.LogConfigSetup;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.kohsuke.args4j.spi.SubCommand;
import org.kohsuke.args4j.spi.SubCommands;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

public abstract class AbstractContainerCommand implements Command {

  protected String getSubcommandsFieldName() {
    return "subcommand";
  }

  protected abstract Optional<Command> getSubcommand();

  protected abstract String getContainerCommandPrefix();

  protected void printUsage(PrintStream stream) {
    String prefix = getContainerCommandPrefix();

    stream.println("buck build tool");

    stream.println("usage:");
    stream.println("  " + prefix + " [options]");
    stream.println("  " + prefix + " command --help");
    stream.println("  " + prefix + " command [command-options]");
    stream.println("available commands:");

    SubCommands subCommands;
    try {
      subCommands = this
          .getClass()
          .getDeclaredField(getSubcommandsFieldName())
          .getAnnotation(SubCommands.class);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
    int lengthOfLongestCommand = 0;
    for (SubCommand subCommand : subCommands.value()) {
      String name = subCommand.name();
      if (name.length() > lengthOfLongestCommand) {
        lengthOfLongestCommand = name.length();
      }
    }

    for (SubCommand subCommand : subCommands.value()) {
      Command command;
      try {
        command = (Command) subCommand.impl().newInstance();
      } catch (IllegalAccessException | InstantiationException e) {
        throw new RuntimeException(e);
      }
      String name = subCommand.name().toLowerCase();
      stream.printf(
          "  %s%s  %s\n",
          name,
          Strings.repeat(" ", lengthOfLongestCommand - name.length()),
          command.getShortDescription());
    }

    stream.println("options:");
    new AdditionalOptionsCmdLineParser(this).printUsage(stream);
  }

  @Override
  public CellConfig getConfigOverrides() {
    Optional<Command> cmd = getSubcommand();
    return cmd.isPresent()
        ? cmd.get().getConfigOverrides()
        : CellConfig.of();
  }

  @Override
  public LogConfigSetup getLogConfig() {
    Optional<Command> cmd = getSubcommand();
    return cmd.isPresent()
        ? cmd.get().getLogConfig()
        : LogConfigSetup.DEFAULT_SETUP;
  }

  @Override
  public boolean isSourceControlStatsGatheringEnabled() {
    return false;
  }

  @Override
  public Iterable<BuckEventListener> getEventListeners(
      Path logDirectoryPath,
      ProjectFilesystem filesystem) {
    return ImmutableList.of();
  }
}
