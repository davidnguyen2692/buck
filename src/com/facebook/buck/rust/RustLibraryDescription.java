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

import static com.facebook.buck.rust.RustLinkables.ruleToCrateName;

import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPlatforms;
import com.facebook.buck.cxx.Linker;
import com.facebook.buck.cxx.LinkerProvider;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractDescriptionArg;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.ToolProvider;
import com.facebook.buck.versions.VersionPropagator;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.util.List;
import java.util.Optional;


public class RustLibraryDescription implements
    Description<RustLibraryDescription.Arg>,
    ImplicitDepsInferringDescription<RustLibraryDescription.Arg>,
    VersionPropagator<RustLibraryDescription.Arg> {

  private final RustBuckConfig rustBuckConfig;
  private final CxxPlatform cxxPlatform;

  public RustLibraryDescription(
      RustBuckConfig rustBuckConfig,
      CxxPlatform cxxPlatform) {
    this.rustBuckConfig = rustBuckConfig;
    this.cxxPlatform = cxxPlatform;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) {
    LinkerProvider linker =
        rustBuckConfig.getLinkerProvider(cxxPlatform, cxxPlatform.getLd().getType());

    ImmutableList.Builder<String> rustcArgs = ImmutableList.builder();

    rustcArgs.addAll(rustBuckConfig.getRustLibraryFlags());
    rustcArgs.addAll(args.rustcFlags);

    return new RustLibrary(
        params,
        new SourcePathResolver(resolver),
        args.crate.orElse(ruleToCrateName(params.getBuildTarget().getShortName())),
        args.crateRoot,
        ImmutableSortedSet.copyOf(args.srcs),
        ImmutableSortedSet.copyOf(args.features),
        rustcArgs.build(),
        () -> rustBuckConfig.getRustCompiler().resolve(resolver),
        () -> linker.resolve(resolver),
        rustBuckConfig.getLinkerArgs(cxxPlatform),
        args.linkStyle.orElse(Linker.LinkableDepType.STATIC));
  }

  @Override
  public Iterable<BuildTarget> findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      Arg constructorArg) {
    ImmutableSet.Builder<BuildTarget> deps = ImmutableSet.builder();

    ToolProvider compiler = rustBuckConfig.getRustCompiler();
    deps.addAll(compiler.getParseTimeDeps());

    deps.addAll(CxxPlatforms.getParseTimeDeps(cxxPlatform));

    return deps.build();
  }


  @SuppressFieldNotInitialized
  public static class Arg extends AbstractDescriptionArg {
    public ImmutableSortedSet<SourcePath> srcs;
    public ImmutableSortedSet<String> features = ImmutableSortedSet.of();
    public List<String> rustcFlags = ImmutableList.of();
    public ImmutableSortedSet<BuildTarget> deps = ImmutableSortedSet.of();
    public Optional<Linker.LinkableDepType> linkStyle;
    public Optional<String> crate;
    public Optional<SourcePath> crateRoot;
  }
}
