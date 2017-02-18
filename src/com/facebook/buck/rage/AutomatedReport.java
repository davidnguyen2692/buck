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
import com.facebook.buck.util.Console;
import com.facebook.buck.util.environment.BuildEnvironmentDescription;
import com.facebook.buck.util.versioncontrol.VersionControlCommandFailedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.Optional;

/**
 * Responsible for gathering logs and other interesting information from buck without user
 * interaction.
 */
public class AutomatedReport extends AbstractReport {
  private final BuildLogHelper buildLogHelper;
  private final Optional<VcsInfoCollector> vcsInfoCollector;
  private final Console console;

  public AutomatedReport(
      DefectReporter defectReporter,
      ProjectFilesystem filesystem,
      ObjectMapper objectMapper,
      Console console,
      BuildEnvironmentDescription buildEnvironmentDescription,
      Optional<VcsInfoCollector> vcsInfoCollector,
      RageConfig rageConfig,
      ExtraInfoCollector extraInfoCollector) {
    super(filesystem,
        defectReporter,
        buildEnvironmentDescription,
        console,
        rageConfig,
        extraInfoCollector,
        Optional.empty());
    this.vcsInfoCollector = vcsInfoCollector;
    this.buildLogHelper = new BuildLogHelper(filesystem, objectMapper);
    this.console = console;
  }

  @Override
  public ImmutableSet<BuildLogEntry> promptForBuildSelection() throws IOException {
    return ImmutableSet.copyOf(buildLogHelper.getBuildLogs());
  }

  @Override
  public Optional<SourceControlInfo> getSourceControlInfo()
      throws IOException, InterruptedException {
    try {
      if (vcsInfoCollector.isPresent()) {
        return Optional.of(vcsInfoCollector.get().gatherScmInformation());
      }
    } catch (VersionControlCommandFailedException e) {
      console.printErrorText(
          "Failed to get source control information: %s, proceeding regardless.", e);
    }
    return Optional.empty();
  }

  @Override
  protected Optional<FileChangesIgnoredReport> getFileChangesIgnoredReport() {
    return Optional.empty();
  }

  @Override
  protected Optional<UserReport> getUserReport() throws IOException {
    return Optional.empty();
  }

}
