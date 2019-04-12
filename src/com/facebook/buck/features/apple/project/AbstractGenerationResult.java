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
package com.facebook.buck.features.apple.project;

import com.facebook.buck.apple.xcode.xcodeproj.PBXProject;
import com.facebook.buck.apple.xcode.xcodeproj.PBXTarget;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.util.immutables.BuckStyleTuple;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.nio.file.Path;
import org.immutables.value.Value;

@Value.Immutable
@BuckStyleTuple
abstract class AbstractGenerationResult {
  public abstract Path getProjectPath();

  public abstract boolean isProjectGenerated();

  public abstract ImmutableSet<BuildTarget> getRequiredBuildTargets();

  public abstract ImmutableSet<Path> getXcconfigPaths();

  public abstract ImmutableList<CopyInXcode> getFilesToCopyInXcode();

  public abstract ImmutableMap<BuildTarget, PBXTarget> getBuildTargetToGeneratedTargetMap();

  public abstract ImmutableSetMultimap<PBXProject, PBXTarget> getGeneratedProjectToPbxTargets();
}
