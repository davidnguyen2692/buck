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

package com.facebook.buck.rust;

import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.util.Verbosity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;

public class RustCompileStep extends ShellStep {

  private final ImmutableMap<String, String> environment;
  private final ImmutableList<String> compilerCommandPrefix;
  private final ImmutableList<String> linkerArgs;
  private final ImmutableList<String> flags;
  private final ImmutableSet<String> features;
  private final Path output;
  private final ImmutableMap<String, Path> crates;
  private final ImmutableSet<Path> crateDeps;
  private final ImmutableSet<Path> nativeDeps;
  private final Path crateRoot;

  public RustCompileStep(
      Path workingDirectory,
      ImmutableMap<String, String> environment,
      ImmutableList<String> compilerCommandPrefix,
      ImmutableList<String> linkerArgs,
      ImmutableList<String> flags,
      ImmutableSet<String> features,
      Path output,
      ImmutableMap<String, Path> crates,
      ImmutableSet<Path> crateDeps,
      ImmutableSet<Path> nativeDeps,
      Path crateRoot) {
    super(workingDirectory);
    this.environment = environment;
    this.compilerCommandPrefix = compilerCommandPrefix;
    this.linkerArgs = linkerArgs;
    this.flags = flags;
    this.features = features;
    this.output = output;
    this.crates = crates;
    this.crateDeps = crateDeps;
    this.nativeDeps = nativeDeps;
    this.crateRoot = crateRoot;
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
    ImmutableList.Builder<String> commandBuilder = ImmutableList.<String>builder()
        .addAll(compilerCommandPrefix);

    // Do colour stuff first so that it can be overridden.
    if (context.getAnsi().isAnsiTerminal()) {
      commandBuilder.add("--color=always");
    }

    if (linkerArgs.size() > 0) {
      commandBuilder.add("-C", String.format("linker=%s", linkerArgs.get(0)));

      for (String arg: linkerArgs.subList(1, linkerArgs.size())) {
        commandBuilder.add("-C", String.format("link-arg=%s", arg));
      }
    }

    commandBuilder
        .addAll(flags)
        .add("-o", output.toString());

    for (String feature : features) {
      commandBuilder.add("--cfg", String.format("feature=\"%s\"", feature));
    }

    for (ImmutableMap.Entry<String, Path> entry : crates.entrySet()) {
      commandBuilder.add(
          "--extern",
          String.format("%s=%s", entry.getKey(), entry.getValue()));
    }

    for (Path path : crateDeps) {
      commandBuilder.add(
          "-L",
          String.format("dependency=%s", path));
    }

    for (Path dir : nativeDeps) {
      commandBuilder.add(
          "-L",
          String.format("native=%s", dir));
    }

    return commandBuilder
        .add(crateRoot.toString())
        .build();
  }

  /*
   * Make sure all stderr output from rustc is emitted, since its either a warning or an error.
   * In general Rust code should have zero warnings, or all warnings as errors. Regardless,
   * respect requests for silence.
   */
  @Override
  protected boolean shouldPrintStderr(Verbosity verbosity) {
    return !verbosity.isSilent();
  }

  @Override
  public ImmutableMap<String, String> getEnvironmentVariables(ExecutionContext context) {
    return environment;
  }

  @Override
  public String getShortName() {
    return "rust compile";
  }
}
