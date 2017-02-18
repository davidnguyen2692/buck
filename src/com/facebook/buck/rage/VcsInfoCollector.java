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

import static com.facebook.buck.util.versioncontrol.VersionControlStatsGenerator.TRACKED_BOOKMARKS;

import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.facebook.buck.util.versioncontrol.VersionControlCmdLineInterface;
import com.facebook.buck.util.versioncontrol.VersionControlCommandFailedException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Map;
import java.util.Optional;

/**
 * Responsible foe getting information out of the version control system.
 */
public class VcsInfoCollector {

  private final VersionControlCmdLineInterface vcCmdLineInterface;

  private VcsInfoCollector(VersionControlCmdLineInterface vcCmdLineInterface) {
    this.vcCmdLineInterface = vcCmdLineInterface;
  }

  public static Optional<VcsInfoCollector> create(
      VersionControlCmdLineInterface vcCmdLineInterface) {
    if (!vcCmdLineInterface.isSupportedVersionControlSystem()) {
      return Optional.empty();
    }
    return Optional.of(new VcsInfoCollector(vcCmdLineInterface));
  }

  public SourceControlInfo gatherScmInformation()
      throws InterruptedException, VersionControlCommandFailedException {
    String currentRevisionId = vcCmdLineInterface.currentRevisionId();
    ImmutableMap<String, String> bookmarksRevisionIds =
        vcCmdLineInterface.bookmarksRevisionsId(TRACKED_BOOKMARKS);
    String baseRevisionId = vcCmdLineInterface.commonAncestor(
        currentRevisionId,
        bookmarksRevisionIds.get("remote/master"));

    ImmutableSet.Builder<String> baseBookmarks = ImmutableSet.builder();
    for (Map.Entry<String, String> bookmark : bookmarksRevisionIds.entrySet()) {
      if (bookmark.getValue().startsWith(baseRevisionId)) {
        baseBookmarks.add(bookmark.getKey());
      }
    }

    return SourceControlInfo.builder()
        .setCurrentRevisionId(currentRevisionId)
        .setRevisionIdOffTracked(baseRevisionId)
        .setBasedOffWhichTracked(baseBookmarks.build())
        .setDiff(vcCmdLineInterface.diffBetweenRevisionsOrAbsent(baseRevisionId, currentRevisionId))
        .setDirtyFiles(vcCmdLineInterface.changedFiles(baseRevisionId))
        .build();
  }

  @Value.Immutable
  @BuckStyleImmutable
  interface AbstractSourceControlInfo {
    String getCurrentRevisionId();
    ImmutableSet<String> getBasedOffWhichTracked();
    Optional<String> getRevisionIdOffTracked();
    @JsonIgnore
    Optional<String> getDiff();
    ImmutableSet<String> getDirtyFiles();
  }
}
