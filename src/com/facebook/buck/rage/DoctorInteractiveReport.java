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
import com.facebook.buck.log.Logger;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.environment.BuildEnvironmentDescription;
import com.facebook.buck.util.versioncontrol.VersionControlCommandFailedException;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.Optional;

/**
 * Responsible for gathering logs and other interesting information from buck when part of the
 * information is already available when calling the constructor.
 */
public class DoctorInteractiveReport extends AbstractReport {

  private static final Logger LOG = Logger.get(DoctorInteractiveReport.class);

  private final Optional<VcsInfoCollector> vcsInfoCollector;
  private final ImmutableSet<BuildLogEntry> buildLogEntries;
  private final UserInput input;

  public DoctorInteractiveReport(
      DefectReporter defectReporter,
      ProjectFilesystem filesystem,
      Console console,
      UserInput input,
      BuildEnvironmentDescription buildEnvironmentDescription,
      Optional<VcsInfoCollector> vcsInfoCollector,
      RageConfig rageConfig,
      ExtraInfoCollector extraInfoCollector,
      ImmutableSet<BuildLogEntry> buildLogEntries,
      Optional<WatchmanDiagReportCollector> watchmanDiagReportCollector) {
    super(filesystem,
        defectReporter,
        buildEnvironmentDescription,
        console,
        rageConfig,
        extraInfoCollector,
        watchmanDiagReportCollector);
    this.input = input;
    this.vcsInfoCollector = vcsInfoCollector;
    this.buildLogEntries = buildLogEntries;
  }

  @Override
  public ImmutableSet<BuildLogEntry> promptForBuildSelection() throws IOException {
    return buildLogEntries;
  }

  @Override
  protected Optional<SourceControlInfo> getSourceControlInfo()
      throws IOException, InterruptedException {
    try {
      if (vcsInfoCollector.isPresent()) {
        return Optional.of(vcsInfoCollector.get().gatherScmInformation());
      }
    } catch (VersionControlCommandFailedException e) {
      LOG.warn("Failed to get source control information: %s, proceeding regardless.\n", e);
    }
    return Optional.empty();
  }

  @Override
  protected Optional<FileChangesIgnoredReport> getFileChangesIgnoredReport()
      throws IOException, InterruptedException {
    return runWatchmanDiagReportCollector(input);
  }

  @Override
  protected Optional<UserReport> getUserReport() {
    return Optional.empty();
  }

}
