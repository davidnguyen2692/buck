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

package com.facebook.buck.jvm.groovy;

import static com.facebook.buck.jvm.common.ResourceValidator.validateResources;

import com.facebook.buck.jvm.java.CalculateAbi;
import com.facebook.buck.jvm.java.DefaultJavaLibrary;
import com.facebook.buck.jvm.java.JavaLibraryRules;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.jvm.java.JavacOptionsFactory;
import com.facebook.buck.jvm.java.JvmLibraryArg;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRules;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

import java.util.Optional;


public class GroovyLibraryDescription implements Description<GroovyLibraryDescription.Arg> {

  private final GroovyBuckConfig groovyBuckConfig;
  // For cross compilation
  private final JavacOptions defaultJavacOptions;

  public GroovyLibraryDescription(
      GroovyBuckConfig groovyBuckConfig,
      JavacOptions defaultJavacOptions) {
    this.groovyBuckConfig = groovyBuckConfig;
    this.defaultJavacOptions = defaultJavacOptions;
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
      A args) throws NoSuchBuildTargetException {
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

    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);

    ImmutableSortedSet<BuildRule> exportedDeps = resolver.getAllRules(args.exportedDeps);
    BuildRuleParams javaLibraryParams =
        params.appendExtraDeps(
            BuildRules.getExportedRules(
                Iterables.concat(
                    params.getDeclaredDeps().get(),
                    exportedDeps,
                    resolver.getAllRules(args.providedDeps))));
    JavacOptions javacOptions = JavacOptionsFactory
        .create(
          defaultJavacOptions,
          params,
          resolver,
          ruleFinder,
          args)
        // groovyc may or may not play nice with generating ABIs from source, so disabling for now
        .withAbiGenerationMode(JavacOptions.AbiGenerationMode.CLASS);
    return new DefaultJavaLibrary(
        javaLibraryParams,
        pathResolver,
        ruleFinder,
        args.srcs,
        validateResources(
            pathResolver,
            params.getProjectFilesystem(),
            args.resources),
        Optional.empty(),
        Optional.empty(),
        ImmutableList.of(),
        exportedDeps,
        resolver.getAllRules(args.providedDeps),
        JavaLibraryRules.getAbiInputs(resolver, javaLibraryParams.getDeps()),
        /* trackClassUsage */ false,
        /* additionalClasspathEntries */ ImmutableSet.of(),
        new GroovycToJarStepFactory(
            groovyBuckConfig.getGroovyCompiler().get(),
            Optional.of(args.extraGroovycArguments),
            javacOptions),
        Optional.empty(),
        /* manifest file */ Optional.empty(),
        Optional.empty(),
        ImmutableSortedSet.of(),
        /* classesToRemoveFromJar */ ImmutableSet.of());
  }

  @SuppressFieldNotInitialized
  public static class Arg extends JvmLibraryArg {
    public ImmutableSortedSet<SourcePath> srcs = ImmutableSortedSet.of();
    public ImmutableSortedSet<SourcePath> resources = ImmutableSortedSet.of();
    public ImmutableList<String> extraGroovycArguments = ImmutableList.of();
    public ImmutableSortedSet<BuildTarget> providedDeps = ImmutableSortedSet.of();
    public ImmutableSortedSet<BuildTarget> exportedDeps = ImmutableSortedSet.of();
    public ImmutableSortedSet<BuildTarget> deps = ImmutableSortedSet.of();
  }
}
