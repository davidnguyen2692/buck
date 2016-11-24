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

package com.facebook.buck.go;

import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPlatforms;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.AbstractDescriptionArg;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.util.List;
import java.util.Optional;

public class GoBinaryDescription implements
    Description<GoBinaryDescription.Arg>,
    ImplicitDepsInferringDescription<GoBinaryDescription.Arg>,
    Flavored {

  private final GoBuckConfig goBuckConfig;

  public GoBinaryDescription(GoBuckConfig goBuckConfig) {
    this.goBuckConfig = goBuckConfig;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    return goBuckConfig.getPlatformFlavorDomain().containsAnyOf(flavors);
  }

  @Override
  public <A extends Arg> BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) throws NoSuchBuildTargetException {
    GoPlatform platform = goBuckConfig.getPlatformFlavorDomain().getValue(params.getBuildTarget())
        .orElse(goBuckConfig.getDefaultPlatform());

    return GoDescriptors.createGoBinaryRule(
        params,
        resolver,
        goBuckConfig,
        args.srcs,
        args.compilerFlags,
        args.assemblerFlags,
        args.linkerFlags,
        platform);
  }

  @Override
  public Iterable<BuildTarget> findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      Arg constructorArg) {

    ImmutableList.Builder<BuildTarget> targets = ImmutableList.builder();

    // Add the C/C++ linker parse time deps.
    GoPlatform goPlatform = goBuckConfig.getPlatformFlavorDomain().getValue(buildTarget)
        .orElse(goBuckConfig.getDefaultPlatform());
    Optional<CxxPlatform> cxxPlatform = goPlatform.getCxxPlatform();
    if (cxxPlatform.isPresent()) {
      targets.addAll(CxxPlatforms.getParseTimeDeps(cxxPlatform.get()));
    }

    return targets.build();
  }

  @SuppressFieldNotInitialized
  public static class Arg extends AbstractDescriptionArg {
    public ImmutableSet<SourcePath> srcs;
    public List<String> compilerFlags = ImmutableList.of();
    public List<String> assemblerFlags = ImmutableList.of();
    public List<String> linkerFlags = ImmutableList.of();
    public ImmutableSortedSet<BuildTarget> deps = ImmutableSortedSet.of();
  }
}
