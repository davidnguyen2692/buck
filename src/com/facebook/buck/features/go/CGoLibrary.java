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

package com.facebook.buck.features.go;

import com.facebook.buck.core.cell.resolver.CellPathResolver;
import com.facebook.buck.core.description.BuildRuleParams;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.rules.impl.NoopBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.SourceWithFlags;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolver;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.cxx.CxxDeps;
import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxLink;
import com.facebook.buck.cxx.CxxLinkAndCompileRules;
import com.facebook.buck.cxx.CxxLinkOptions;
import com.facebook.buck.cxx.CxxLinkableEnhancer;
import com.facebook.buck.cxx.CxxSource;
import com.facebook.buck.cxx.toolchain.CxxBuckConfig;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.linker.Linker;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.FileListableLinkerInputArg;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The CGoLibrary represents cgo build process which outputs the linkable object that is appended to
 * the native go compiled program (via pack tool).
 *
 * <p>The process consists of four steps (similiar to go build): 1. Generate c sources with cgo tool
 * 2. Compile and link cgo sources into single object 3. Generate cgo_import.go 4. Return generated
 * go files and linked object (used by GoCompile)
 */
public class CGoLibrary extends NoopBuildRuleWithDeclaredAndExtraDeps {
  private final ImmutableList<SourcePath> goFiles;
  private final SourcePath output;
  private final Iterable<BuildRule> linkableDeps;

  private CGoLibrary(
      BuildRuleParams params,
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      ImmutableList<SourcePath> goFiles,
      SourcePath output,
      Iterable<BuildRule> linkableDeps) {
    super(buildTarget, projectFilesystem, params);

    this.goFiles = goFiles;
    this.output = output;
    this.linkableDeps = linkableDeps;
  }

  public static BuildRule create(
      BuildRuleParams params,
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      ActionGraphBuilder graphBuilder,
      SourcePathResolver pathResolver,
      CellPathResolver cellRoots,
      CxxBuckConfig cxxBuckConfig,
      GoPlatform platform,
      CgoLibraryDescriptionArg args,
      Iterable<BuildTarget> cxxDeps,
      Tool cgo,
      Path packageName) {

    CxxDeps allDeps =
        CxxDeps.builder().addDeps(cxxDeps).addPlatformDeps(args.getPlatformDeps()).build();

    // generate C sources with cgo tool (go build writes c files to _obj dir)
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(graphBuilder);
    ImmutableMap<Path, SourcePath> headers =
        CxxDescriptionEnhancer.parseHeaders(
            buildTarget,
            graphBuilder,
            ruleFinder,
            pathResolver,
            Optional.of(platform.getCxxPlatform()),
            args);

    CGoGenSource genSource =
        (CGoGenSource)
            graphBuilder.computeIfAbsent(
                buildTarget.withAppendedFlavors(InternalFlavor.of("cgo-gen-sources")),
                target ->
                    new CGoGenSource(
                        target,
                        projectFilesystem,
                        ruleFinder,
                        pathResolver,
                        args.getSrcs()
                            .stream()
                            .map(x -> x.getSourcePath())
                            .collect(ImmutableSet.toImmutableSet()),
                        cgo,
                        args.getCgoCompilerFlags(),
                        platform));

    // generated c files needs to be compiled and linked into a single object
    // file (equivalent of (_cgo_.o), includes:
    //   * _cgo_export.o
    //   * _cgo_main.o
    //   * all of the *.cgo2.o
    CxxLink cgoBin =
        (CxxLink)
            graphBuilder.computeIfAbsent(
                buildTarget.withAppendedFlavors(InternalFlavor.of("cgo-first-step")),
                target ->
                    nativeBinCompilation(
                        target,
                        projectFilesystem,
                        graphBuilder,
                        pathResolver,
                        cellRoots,
                        cxxBuckConfig,
                        platform.getCxxPlatform(),
                        args,
                        new ImmutableList.Builder<BuildRule>()
                            .add(genSource)
                            .addAll(allDeps.get(graphBuilder, platform.getCxxPlatform()))
                            .build(),
                        new ImmutableMap.Builder<Path, SourcePath>()
                            .putAll(headers)
                            .put(
                                pathResolver
                                    .getAbsolutePath(genSource.getExportHeader())
                                    .getFileName(),
                                genSource.getExportHeader())
                            .build(),
                        new ImmutableList.Builder<SourcePath>()
                            .addAll(genSource.getCFiles())
                            .addAll(genSource.getCgoFiles())
                            .build(),
                        args.getLinkerFlags()));

    // generate cgo_import.h with previously generated object file (_cgo.o)
    BuildRule cgoImport =
        graphBuilder.computeIfAbsent(
            buildTarget.withAppendedFlavors(InternalFlavor.of("cgo-gen-import")),
            target ->
                new CGoGenImport(
                    target,
                    projectFilesystem,
                    ruleFinder,
                    pathResolver,
                    cgo,
                    platform,
                    packageName,
                    Preconditions.checkNotNull(cgoBin.getSourcePathToOutput())));

    // generate final object file (equivalent of _all.o) which includes:
    //  * _cgo_export.o
    //  * all of the *.cgo2.o files
    ImmutableList<Arg> cxxArgs =
        ImmutableList.<Arg>builder()
            .addAll(StringArg.from("-r", "-nostdlib"))
            .addAll(
                cgoBin
                    .getArgs()
                    .stream()
                    .filter(FileListableLinkerInputArg.class::isInstance)
                    .map(FileListableLinkerInputArg.class::cast)
                    .filter(
                        arg -> {
                          String fileName =
                              pathResolver.getAbsolutePath(arg.getPath()).getFileName().toString();
                          return fileName.contains(".cgo2.c") || fileName.contains("_cgo_export.c");
                        })
                    .collect(ImmutableList.toImmutableList()))
            .build();

    CxxLink cgoAllBin =
        (CxxLink)
            graphBuilder.computeIfAbsent(
                buildTarget.withAppendedFlavors(InternalFlavor.of("cgo-second-step")),
                target ->
                    CxxLinkableEnhancer.createCxxLinkableBuildRule(
                        cellRoots,
                        cxxBuckConfig,
                        platform.getCxxPlatform(),
                        projectFilesystem,
                        graphBuilder,
                        ruleFinder,
                        target,
                        BuildTargets.getGenPath(projectFilesystem, target, "%s/_all"),
                        ImmutableMap.of(),
                        cxxArgs, // collection of selected object files
                        args.getLinkStyle().orElse(Linker.LinkableDepType.STATIC),
                        CxxLinkOptions.of(),
                        Optional.empty()));

    // output (referenced later on by GoCompile) provides:
    // * _cgo_gotypes.go
    // * _cgo_import.go
    // * all of the *.cgo1.go files
    //
    // the go sources should be appended to sources list and _all.o file should
    // be appended to the output binary (pack step)
    return graphBuilder.computeIfAbsent(
        buildTarget,
        target ->
            new CGoLibrary(
                params
                    .withDeclaredDeps(
                        ImmutableSortedSet.<BuildRule>naturalOrder()
                            .addAll(
                                ruleFinder.filterBuildRuleInputs(cgoAllBin.getSourcePathToOutput()))
                            .addAll(
                                ruleFinder.filterBuildRuleInputs(
                                    new ImmutableList.Builder<SourcePath>()
                                        .addAll(genSource.getGoFiles())
                                        .add(
                                            Preconditions.checkNotNull(
                                                cgoImport.getSourcePathToOutput()))
                                        .build()))
                            .build())
                    .withoutExtraDeps(),
                target,
                projectFilesystem,
                new ImmutableList.Builder<SourcePath>()
                    .addAll(genSource.getGoFiles())
                    .add(Preconditions.checkNotNull(cgoImport.getSourcePathToOutput()))
                    .build(),
                Preconditions.checkNotNull(cgoAllBin.getSourcePathToOutput()),
                Preconditions.checkNotNull(allDeps.get(graphBuilder, platform.getCxxPlatform()))));
  }

  private static CxxLink nativeBinCompilation(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      ActionGraphBuilder graphBuilder,
      SourcePathResolver pathResolver,
      CellPathResolver cellRoots,
      CxxBuckConfig cxxBuckConfig,
      CxxPlatform cxxPlatform,
      CgoLibraryDescriptionArg args,
      Iterable<BuildRule> deps,
      ImmutableMap<Path, SourcePath> headers,
      ImmutableList<SourcePath> sources,
      ImmutableList<StringWithMacros> flags) {

    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(graphBuilder);
    ImmutableMap<String, CxxSource> srcs =
        CxxDescriptionEnhancer.parseCxxSources(
            buildTarget,
            graphBuilder,
            ruleFinder,
            pathResolver,
            cxxPlatform,
            wrapSourcePathsWithFlags(sources),
            PatternMatchedCollection.of());

    CxxLinkAndCompileRules cxxLinkAndCompileRules =
        CxxDescriptionEnhancer.createBuildRulesForCxxBinary(
            buildTarget,
            projectFilesystem,
            graphBuilder,
            cellRoots,
            cxxBuckConfig,
            cxxPlatform,
            srcs,
            headers,
            ImmutableSortedSet.<BuildRule>naturalOrder().addAll(deps).build(),
            ImmutableSet.of(),
            Optional.empty(),
            Optional.empty(),
            args.getLinkStyle().orElse(Linker.LinkableDepType.STATIC_PIC),
            CxxLinkOptions.of(),
            args.getPreprocessorFlags(),
            args.getPlatformPreprocessorFlags(),
            args.getLangPreprocessorFlags(),
            args.getLangPlatformPreprocessorFlags(),
            ImmutableSortedSet.of(),
            ImmutableSortedSet.of(),
            args.getCompilerFlags(),
            args.getLangCompilerFlags(),
            args.getPlatformCompilerFlags(),
            args.getLangPlatformCompilerFlags(),
            Optional.empty(),
            Optional.empty(),
            flags,
            args.getLinkerExtraOutputs(),
            args.getPlatformLinkerFlags(),
            Optional.empty(),
            args.getIncludeDirs(),
            args.getRawHeaders());

    return cxxLinkAndCompileRules.getCxxLink();
  }

  private static ImmutableSortedSet<SourceWithFlags> wrapSourcePathsWithFlags(
      Iterable<SourcePath> it) {
    ImmutableSortedSet.Builder<SourceWithFlags> builder = ImmutableSortedSet.naturalOrder();
    for (SourcePath sourcePath : it) {
      builder.add(SourceWithFlags.of(sourcePath));
    }
    return builder.build();
  }

  /** returns .go files produced by cgo tool */
  public ImmutableList<SourcePath> getGeneratedGoSource() {
    return goFiles;
  }

  /** returns compiled linkable file source path */
  public SourcePath getOutput() {
    return output;
  }

  public Iterable<BuildRule> getLinkableDeps() {
    return linkableDeps;
  }
}
