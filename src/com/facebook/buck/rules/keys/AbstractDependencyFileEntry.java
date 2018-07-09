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
package com.facebook.buck.rules.keys;

import com.facebook.buck.core.sourcepath.ArchiveMemberSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolver;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.facebook.buck.io.ArchiveMemberPath;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.util.Optional;
import org.immutables.value.Value;

@BuckStyleImmutable
@JsonSerialize
@Value.Immutable
abstract class AbstractDependencyFileEntry {
  @Value.Parameter
  public abstract Path pathToFile();

  @Value.Parameter
  public abstract Optional<Path> pathWithinArchive();

  @Value.Check
  protected void check() {
    Preconditions.checkState(!pathToFile().isAbsolute());
    Preconditions.checkState(
        !pathWithinArchive().isPresent() || !pathWithinArchive().get().isAbsolute());
  }

  public static DependencyFileEntry fromSourcePath(
      SourcePath sourcePath, SourcePathResolver resolver) {
    DependencyFileEntry.Builder builder = DependencyFileEntry.builder();
    if (sourcePath instanceof ArchiveMemberSourcePath) {
      ArchiveMemberSourcePath archiveMemberSourcePath = (ArchiveMemberSourcePath) sourcePath;
      ArchiveMemberPath relativeArchiveMemberPath =
          resolver.getRelativeArchiveMemberPath(archiveMemberSourcePath);
      builder.setPathToFile(relativeArchiveMemberPath.getArchivePath());
      builder.setPathWithinArchive(relativeArchiveMemberPath.getMemberPath());
    } else {
      builder.setPathToFile(resolver.getRelativePath(sourcePath));
    }

    return builder.build();
  }
}
