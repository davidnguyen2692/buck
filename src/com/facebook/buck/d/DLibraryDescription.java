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

package com.facebook.buck.d;

import com.facebook.buck.cxx.Archive;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxSourceRuleFactory;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.AbstractDescriptionArg;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.versions.VersionPropagator;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.Path;

public class DLibraryDescription implements
    Description<DLibraryDescription.Arg>,
    VersionPropagator<DLibraryDescription.Arg> {

  private final DBuckConfig dBuckConfig;
  private final CxxBuckConfig cxxBuckConfig;
  private final CxxPlatform cxxPlatform;

  public DLibraryDescription(
      DBuckConfig dBuckConfig,
      CxxBuckConfig cxxBuckConfig,
      CxxPlatform cxxPlatform) {
    this.dBuckConfig = dBuckConfig;
    this.cxxBuckConfig = cxxBuckConfig;
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
      BuildRuleResolver buildRuleResolver,
      A args)
      throws NoSuchBuildTargetException {

    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(buildRuleResolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);

    if (params.getBuildTarget().getFlavors().contains(DDescriptionUtils.SOURCE_LINK_TREE)) {
      return DDescriptionUtils.createSourceSymlinkTree(
          params.getBuildTarget(),
          params,
          ruleFinder,
          pathResolver,
          args.srcs);
    }

    BuildTarget sourceTreeTarget =
        params.getBuildTarget().withAppendedFlavors(DDescriptionUtils.SOURCE_LINK_TREE);
    DIncludes dIncludes =
        DIncludes.builder()
            .setLinkTree(new BuildTargetSourcePath(sourceTreeTarget))
            .setSources(args.srcs.getPaths())
            .build();

    if (params.getBuildTarget().getFlavors().contains(CxxDescriptionEnhancer.STATIC_FLAVOR)) {
      buildRuleResolver.requireRule(sourceTreeTarget);
      return createStaticLibraryBuildRule(
          params,
          buildRuleResolver,
          pathResolver,
          ruleFinder,
          cxxPlatform,
          dBuckConfig,
          /* compilerFlags */ ImmutableList.of(),
          args.srcs,
          dIncludes,
          CxxSourceRuleFactory.PicType.PDC);
    }

    return new DLibrary(
        params,
        buildRuleResolver,
        pathResolver,
        dIncludes);
  }

  /**
   * @return a BuildRule that creates a static library.
   */
  private BuildRule createStaticLibraryBuildRule(
      BuildRuleParams params,
      BuildRuleResolver ruleResolver,
      SourcePathResolver pathResolver,
      SourcePathRuleFinder ruleFinder,
      CxxPlatform cxxPlatform,
      DBuckConfig dBuckConfig,
      ImmutableList<String> compilerFlags,
      SourceList sources,
      DIncludes dIncludes,
      CxxSourceRuleFactory.PicType pic)
      throws NoSuchBuildTargetException {

    ImmutableList<SourcePath> compiledSources =
        DDescriptionUtils.sourcePathsForCompiledSources(
            params,
            ruleResolver,
            pathResolver,
            ruleFinder,
            cxxPlatform,
            dBuckConfig,
            compilerFlags,
            sources,
            dIncludes);

    // Write a build rule to create the archive for this library.
    BuildTarget staticTarget =
        CxxDescriptionEnhancer.createStaticLibraryBuildTarget(
            params.getBuildTarget(),
            cxxPlatform.getFlavor(),
            pic);

    Path staticLibraryPath =
        CxxDescriptionEnhancer.getStaticLibraryPath(
            params.getProjectFilesystem(),
            params.getBuildTarget(),
            cxxPlatform.getFlavor(),
            pic,
            cxxPlatform.getStaticLibraryExtension());

    return Archive.from(
        staticTarget,
        params,
        pathResolver,
        ruleFinder,
        cxxPlatform,
        cxxBuckConfig.getArchiveContents(),
        staticLibraryPath,
        compiledSources);
  }

  @SuppressFieldNotInitialized
  public static class Arg extends AbstractDescriptionArg {
    public SourceList srcs;
    public ImmutableSortedSet<BuildTarget> deps = ImmutableSortedSet.of();
    public ImmutableList<String> linkerFlags = ImmutableList.of();
  }
}
