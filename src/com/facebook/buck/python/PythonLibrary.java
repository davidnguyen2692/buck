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

import static com.facebook.buck.rules.BuildableProperties.Kind.LIBRARY;

import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableProperties;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.NoopBuildRule;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class PythonLibrary extends NoopBuildRule implements PythonPackagable, HasRuntimeDeps {

  private static final BuildableProperties OUTPUT_TYPE = new BuildableProperties(LIBRARY);

  private final Function<? super PythonPlatform, ImmutableMap<Path, SourcePath>> srcs;
  private final Function<? super PythonPlatform, ImmutableMap<Path, SourcePath>> resources;
  private final Optional<Boolean> zipSafe;

  public PythonLibrary(
      BuildRuleParams params,
      SourcePathResolver resolver,
      Function<? super PythonPlatform, ImmutableMap<Path, SourcePath>> srcs,
      Function<? super PythonPlatform, ImmutableMap<Path, SourcePath>> resources,
      Optional<Boolean> zipSafe) {
    super(params, resolver);
    this.srcs = srcs;
    this.resources = resources;
    this.zipSafe = zipSafe;
  }

  @Override
  public PythonPackageComponents getPythonPackageComponents(
      PythonPlatform pythonPlatform,
      CxxPlatform cxxPlatform) {
    return PythonPackageComponents.of(
        Preconditions.checkNotNull(srcs.apply(pythonPlatform)),
        Preconditions.checkNotNull(resources.apply(pythonPlatform)),
        ImmutableMap.of(),
        ImmutableSet.of(),
        zipSafe);
  }

  @Override
  public BuildableProperties getProperties() {
    return OUTPUT_TYPE;
  }

  public ImmutableMap<Path, SourcePath> getSrcs(PythonPlatform pythonPlatform) {
    return Preconditions.checkNotNull(srcs.apply(pythonPlatform));
  }

  public ImmutableMap<Path, SourcePath> getResources(PythonPlatform pythonPlatform) {
    return Preconditions.checkNotNull(resources.apply(pythonPlatform));
  }

  @Override
  public Stream<BuildTarget> getRuntimeDeps() {
    return getDeclaredDeps().stream().map(BuildRule::getBuildTarget);
  }

}
