/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.rules;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Pair;
import com.google.common.primitives.Booleans;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A {@link SourcePath} that utilizes the output from the {@link BuildRule} referenced by a
 * {@link com.facebook.buck.model.BuildTarget} as the file it represents.
 */
public class BuildTargetSourcePath extends AbstractSourcePath<BuildTargetSourcePath> {

  private final BuildTarget target;
  private final Optional<Path> resolvedPath;

  public BuildTargetSourcePath(BuildTarget target) {
    this(target, Optional.empty());
  }

  public BuildTargetSourcePath(BuildTarget target, Path path) {
    this(target, Optional.of(path));
  }

  private BuildTargetSourcePath(
      BuildTarget target,
      Optional<Path> path) {
    this.target = target;
    this.resolvedPath = path;
  }

  public Optional<Path> getResolvedPath() {
    return resolvedPath;
  }

  @Override
  protected Object asReference() {
    return new Pair<>(target, resolvedPath);
  }

  @Override
  protected int compareReferences(BuildTargetSourcePath o) {
    if (o == this) {
      return 0;
    }

    int res;

    res = target.compareTo(o.target);
    if (res != 0) {
      return res;
    }

    res = Booleans.compare(o.resolvedPath.isPresent(), resolvedPath.isPresent());
    if (res != 0) {
      return res;
    }

    if (resolvedPath.isPresent() && o.resolvedPath.isPresent()) {
      res = resolvedPath.get().compareTo(o.resolvedPath.get());
      if (res != 0) {
        return res;
      }
    }

    return 0;
  }

  public BuildTarget getTarget() {
    return target;
  }

}
