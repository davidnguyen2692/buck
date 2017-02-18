/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.thrift;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.CalculateAbi;
import com.facebook.buck.jvm.java.DefaultJavaLibrary;
import com.facebook.buck.jvm.java.JavaLibraryRules;
import com.facebook.buck.jvm.java.Javac;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.jvm.java.JavacOptionsAmender;
import com.facebook.buck.jvm.java.JavacToJarStepFactory;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.model.UnflavoredBuildTarget;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRules;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.SourcePaths;
import com.facebook.buck.rules.TargetGraph;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;

import java.nio.file.Path;
import java.util.Optional;

public class ThriftJavaEnhancer implements ThriftLanguageSpecificEnhancer {

  private static final Flavor JAVA_FLAVOR = ImmutableFlavor.of("java");

  private final ThriftBuckConfig thriftBuckConfig;
  private final JavacOptions templateOptions;

  public ThriftJavaEnhancer(
      ThriftBuckConfig thriftBuckConfig,
      JavacOptions templateOptions) {
    this.thriftBuckConfig = thriftBuckConfig;
    this.templateOptions = templateOptions;
  }

  @Override
  public String getLanguage() {
    return "java";
  }

  @Override
  public Flavor getFlavor() {
    return JAVA_FLAVOR;
  }

  @Override
  public ImmutableSortedSet<String> getGeneratedSources(
      BuildTarget target,
      ThriftConstructorArg args,
      String thriftName,
      ImmutableList<String> services) {
    return ImmutableSortedSet.of("");
  }

  @VisibleForTesting
  protected BuildTarget getSourceZipBuildTarget(UnflavoredBuildTarget target, String name) {
    return BuildTargets.createFlavoredBuildTarget(
        target,
        ImmutableFlavor.of(
            String.format(
                "thrift-java-source-zip-%s",
                name.replace('/', '-').replace('.', '-').replace('+', '-').replace(' ', '-'))));
  }

  private Path getSourceZipOutputPath(
      ProjectFilesystem filesystem,
      UnflavoredBuildTarget target,
      String name) {
    BuildTarget flavoredTarget = getSourceZipBuildTarget(target, name);
    return BuildTargets.getScratchPath(filesystem, flavoredTarget, "%s" + Javac.SRC_ZIP);
  }

  @Override
  public BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      ThriftConstructorArg args,
      ImmutableMap<String, ThriftSource> sources,
      ImmutableSortedSet<BuildRule> deps) throws NoSuchBuildTargetException {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);

    if (CalculateAbi.isAbiTarget(params.getBuildTarget())) {
      BuildTarget libraryTarget = CalculateAbi.getLibraryTarget(params.getBuildTarget());
      resolver.requireRule(libraryTarget);
      return CalculateAbi.of(
          params.getBuildTarget(),
          ruleFinder,
          params,
          new BuildTargetSourcePath(libraryTarget));
    }

    // Pack all the generated sources into a single source zip that we'll pass to the
    // java rule below.
    ImmutableSortedSet.Builder<BuildRule> sourceZipsBuilder = ImmutableSortedSet.naturalOrder();
    UnflavoredBuildTarget unflavoredBuildTarget =
        params.getBuildTarget().getUnflavoredBuildTarget();
    for (ImmutableMap.Entry<String, ThriftSource> ent : sources.entrySet()) {
      String name = ent.getKey();
      BuildRule compilerRule = ent.getValue().getCompileRule();
      Path sourceDirectory = ent.getValue().getOutputDir().resolve("gen-java");

      BuildTarget sourceZipTarget = getSourceZipBuildTarget(unflavoredBuildTarget, name);
      Path sourceZip =
          getSourceZipOutputPath(params.getProjectFilesystem(), unflavoredBuildTarget, name);

      sourceZipsBuilder.add(
          new SrcZip(
              params.copyWithChanges(
                  sourceZipTarget,
                  Suppliers.ofInstance(ImmutableSortedSet.of(compilerRule)),
                  Suppliers.ofInstance(ImmutableSortedSet.of())),
              sourceZip,
              sourceDirectory));
    }
    ImmutableSortedSet<BuildRule> sourceZips = sourceZipsBuilder.build();
    resolver.addAllToIndex(sourceZips);

    // Create to main compile rule.
    BuildRuleParams javaParams = params.copyWithChanges(
        BuildTargets.createFlavoredBuildTarget(
            unflavoredBuildTarget,
            getFlavor()),
        Suppliers.ofInstance(
            ImmutableSortedSet.<BuildRule>naturalOrder()
                .addAll(sourceZips)
                .addAll(deps)
                .addAll(BuildRules.getExportedRules(deps))
                .addAll(ruleFinder.filterBuildRuleInputs(templateOptions.getInputs(ruleFinder)))
                .build()),
        Suppliers.ofInstance(ImmutableSortedSet.of()));

    final SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);

    return new DefaultJavaLibrary(
        javaParams,
        pathResolver,
        ruleFinder,
        FluentIterable.from(sourceZips)
            .transform(SourcePaths.getToBuildTargetSourcePath())
            .toSortedSet(Ordering.natural()),
        /* resources */ ImmutableSet.of(),
        templateOptions.getGeneratedSourceFolderName(),
        /* proguardConfig */ Optional.empty(),
        /* postprocessClassesCommands */ ImmutableList.of(),
        /* exportedDeps */ ImmutableSortedSet.of(),
        /* providedDeps */ ImmutableSortedSet.of(),
        JavaLibraryRules.getAbiInputs(resolver, javaParams.getDeps()),
        templateOptions.trackClassUsage(),
        /* additionalClasspathEntries */ ImmutableSet.of(),
        new JavacToJarStepFactory(templateOptions, JavacOptionsAmender.IDENTITY),
        /* resourcesRoot */ Optional.empty(),
        /* manifest file */ Optional.empty(),
        /* mavenCoords */ Optional.empty(),
        /* tests */ ImmutableSortedSet.of(),
        /* classesToRemoveFromJar */ ImmutableSet.of());
  }

  private ImmutableSet<BuildTarget> getImplicitDeps() {
    return ImmutableSet.of(thriftBuckConfig.getJavaDep());
  }

  @Override
  public ImmutableSet<BuildTarget> getImplicitDepsForTargetFromConstructorArg(
      BuildTarget target,
      ThriftConstructorArg args) {
    return getImplicitDeps();
  }

  @Override
  public ImmutableSet<String> getOptions(BuildTarget target, ThriftConstructorArg arg) {
    return arg.javaOptions;
  }

  @Override
  public ThriftLibraryDescription.CompilerType getCompilerType() {
    return ThriftLibraryDescription.CompilerType.THRIFT;
  }

}
