/*
 * Copyright 2012-present Facebook, Inc.
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

package com.facebook.buck.python;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AbstractBuildRuleWithResolver;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BinaryBuildRule;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.SourcePathResolver;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;
import java.util.stream.Stream;

public abstract class PythonBinary
    extends AbstractBuildRuleWithResolver
    implements BinaryBuildRule, HasRuntimeDeps {

  private final PythonPlatform pythonPlatform;
  private final String mainModule;
  private final PythonPackageComponents components;
  private final ImmutableSet<String> preloadLibraries;
  @AddToRuleKey
  private final String pexExtension;
  @AddToRuleKey
  private final boolean legacyOutputPath;

  public PythonBinary(
      BuildRuleParams buildRuleParams,
      SourcePathResolver resolver,
      PythonPlatform pythonPlatform,
      String mainModule,
      PythonPackageComponents components,
      ImmutableSet<String> preloadLibraries,
      String pexExtension,
      boolean legacyOutputPath) {
    super(buildRuleParams, resolver);
    this.pythonPlatform = pythonPlatform;
    this.mainModule = mainModule;
    this.components = components;
    this.preloadLibraries = preloadLibraries;
    this.pexExtension = pexExtension;
    this.legacyOutputPath = legacyOutputPath;
  }

  protected final Path getBinPath() {
    BuildTarget buildTarget = getBuildTarget();
    if (!legacyOutputPath) {
      buildTarget = buildTarget.withFlavors();
    }
    return BuildTargets.getGenPath(getProjectFilesystem(), buildTarget, "%s" + pexExtension);
  }

  @Override
  public final Path getPathToOutput() {
    return getBinPath();
  }

  @VisibleForTesting
  protected final PythonPlatform getPythonPlatform() {
    return pythonPlatform;
  }

  @VisibleForTesting
  protected final String getMainModule() {
    return mainModule;
  }

  @VisibleForTesting
  protected final PythonPackageComponents getComponents() {
    return components;
  }

  @VisibleForTesting
  protected final ImmutableSet<String> getPreloadLibraries() {
    return preloadLibraries;
  }

  @Override
  public Stream<BuildTarget> getRuntimeDeps() {
    return getDeclaredDeps().stream().map(BuildRule::getBuildTarget);
  }

}
