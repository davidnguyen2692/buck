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

package com.facebook.buck.cxx;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.ImplicitFlavorsInferringDescription;
import com.facebook.buck.rules.MetadataProvidingDescription;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.query.Query;
import com.facebook.buck.rules.query.QueryUtils;
import com.facebook.buck.versions.VersionRoot;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;

import java.util.Optional;
import java.util.Set;

public class CxxBinaryDescription implements
    Description<CxxBinaryDescription.Arg>,
    Flavored,
    ImplicitDepsInferringDescription<CxxBinaryDescription.Arg>,
    ImplicitFlavorsInferringDescription,
    MetadataProvidingDescription<CxxBinaryDescription.Arg>,
    VersionRoot<CxxBinaryDescription.Arg> {

  private final CxxBuckConfig cxxBuckConfig;
  private final InferBuckConfig inferBuckConfig;
  private final CxxPlatform defaultCxxPlatform;
  private final FlavorDomain<CxxPlatform> cxxPlatforms;

  public CxxBinaryDescription(
      CxxBuckConfig cxxBuckConfig,
      InferBuckConfig inferBuckConfig,
      CxxPlatform defaultCxxPlatform,
      FlavorDomain<CxxPlatform> cxxPlatforms) {
    this.cxxBuckConfig = cxxBuckConfig;
    this.inferBuckConfig = inferBuckConfig;
    this.defaultCxxPlatform = defaultCxxPlatform;
    this.cxxPlatforms = cxxPlatforms;
  }

  /**
   * @return a {@link com.facebook.buck.cxx.HeaderSymlinkTree} for the headers of this C/C++ binary.
   */
  public static <A extends Arg> HeaderSymlinkTree createHeaderSymlinkTreeBuildRule(
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CxxPlatform cxxPlatform,
      A args) {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);
    return CxxDescriptionEnhancer.createHeaderSymlinkTree(
        params,
        resolver,
        cxxPlatform,
        CxxDescriptionEnhancer.parseHeaders(
            params.getBuildTarget(),
            pathResolver,
            Optional.of(cxxPlatform),
            args),
        HeaderVisibility.PRIVATE,
        true);
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @SuppressWarnings("PMD.PrematureDeclaration")
  @Override
  public <A extends Arg> BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) throws NoSuchBuildTargetException {

    // We explicitly remove some flavors below from params to make sure rule
    // has the same output regardless if we will strip or not.
    Optional<StripStyle> flavoredStripStyle =
        StripStyle.FLAVOR_DOMAIN.getValue(params.getBuildTarget());
    Optional<LinkerMapMode> flavoredLinkerMapMode =
        LinkerMapMode.FLAVOR_DOMAIN.getValue(params.getBuildTarget());
    params = CxxStrip.removeStripStyleFlavorInParams(params, flavoredStripStyle);
    params = LinkerMapMode.removeLinkerMapModeFlavorInParams(params, flavoredLinkerMapMode);

    // Extract the platform from the flavor, falling back to the default platform if none are
    // found.
    ImmutableSet<Flavor> flavors = ImmutableSet.copyOf(params.getBuildTarget().getFlavors());
    CxxPlatform cxxPlatform = cxxPlatforms
        .getValue(flavors).orElse(defaultCxxPlatform);
    if (flavors.contains(CxxDescriptionEnhancer.HEADER_SYMLINK_TREE_FLAVOR)) {
      flavors = ImmutableSet.copyOf(
          Sets.difference(
              flavors,
              ImmutableSet.of(CxxDescriptionEnhancer.HEADER_SYMLINK_TREE_FLAVOR)));
      BuildTarget target = BuildTarget
          .builder(params.getBuildTarget().getUnflavoredBuildTarget())
          .addAllFlavors(flavors)
          .build();
      BuildRuleParams typeParams =
          params.copyWithChanges(
              target,
              params.getDeclaredDeps(),
              params.getExtraDeps());

      return createHeaderSymlinkTreeBuildRule(
          typeParams,
          resolver,
          cxxPlatform,
          args);
    }

    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);

    if (flavors.contains(CxxCompilationDatabase.COMPILATION_DATABASE)) {
      BuildRuleParams paramsWithoutFlavor =
          params.withoutFlavor(CxxCompilationDatabase.COMPILATION_DATABASE);
      CxxLinkAndCompileRules cxxLinkAndCompileRules = CxxDescriptionEnhancer
          .createBuildRulesForCxxBinaryDescriptionArg(
              targetGraph,
              paramsWithoutFlavor,
              resolver,
              cxxBuckConfig,
              cxxPlatform,
              args,
              flavoredStripStyle,
              flavoredLinkerMapMode);
      return CxxCompilationDatabase.createCompilationDatabase(
          params,
          cxxLinkAndCompileRules.compileRules);
    }

    if (flavors.contains(CxxCompilationDatabase.UBER_COMPILATION_DATABASE)) {
      return CxxDescriptionEnhancer.createUberCompilationDatabase(
          cxxPlatforms.getValue(flavors).isPresent() ?
              params :
              params.withFlavor(defaultCxxPlatform.getFlavor()),
          resolver);
    }

    if (flavors.contains(CxxInferEnhancer.InferFlavors.INFER.get())) {
      return CxxInferEnhancer.requireInferAnalyzeAndReportBuildRuleForCxxDescriptionArg(
          params,
          resolver,
          cxxBuckConfig,
          cxxPlatform,
          args,
          inferBuckConfig,
          new CxxInferSourceFilter(inferBuckConfig));
    }

    if (flavors.contains(CxxInferEnhancer.InferFlavors.INFER_ANALYZE.get())) {
      return CxxInferEnhancer.requireInferAnalyzeBuildRuleForCxxDescriptionArg(
          params,
          resolver,
          cxxBuckConfig,
          cxxPlatform,
          args,
          inferBuckConfig,
          new CxxInferSourceFilter(inferBuckConfig));
    }

    if (flavors.contains(CxxInferEnhancer.InferFlavors.INFER_CAPTURE_ALL.get())) {
      return CxxInferEnhancer.requireAllTransitiveCaptureBuildRules(
          params,
          resolver,
          cxxBuckConfig,
          cxxPlatform,
          inferBuckConfig,
          new CxxInferSourceFilter(inferBuckConfig),
          args);
    }

    if (flavors.contains(CxxInferEnhancer.InferFlavors.INFER_CAPTURE_ONLY.get())) {
      return CxxInferEnhancer.requireInferCaptureAggregatorBuildRuleForCxxDescriptionArg(
          params,
          resolver,
          pathResolver,
          cxxBuckConfig,
          cxxPlatform,
          args,
          inferBuckConfig,
          new CxxInferSourceFilter(inferBuckConfig));
    }

    if (flavors.contains(CxxDescriptionEnhancer.SANDBOX_TREE_FLAVOR)) {
      return CxxDescriptionEnhancer.createSandboxTreeBuildRule(
          resolver,
          args,
          cxxPlatform,
          params);
    }

    CxxLinkAndCompileRules cxxLinkAndCompileRules =
        CxxDescriptionEnhancer.createBuildRulesForCxxBinaryDescriptionArg(
            targetGraph,
            params,
            resolver,
            cxxBuckConfig,
            cxxPlatform,
            args,
            flavoredStripStyle,
            flavoredLinkerMapMode);

    // Return a CxxBinary rule as our representative in the action graph, rather than the CxxLink
    // rule above for a couple reasons:
    //  1) CxxBinary extends BinaryBuildRule whereas CxxLink does not, so the former can be used
    //     as executables for genrules.
    //  2) In some cases, users add dependencies from some rules onto other binary rules, typically
    //     if the binary is executed by some test or library code at test time.  These target graph
    //     deps should *not* become build time dependencies on the CxxLink step, otherwise we'd
    //     have to wait for the dependency binary to link before we could link the dependent binary.
    //     By using another BuildRule, we can keep the original target graph dependency tree while
    //     preventing it from affecting link parallelism.

    params = CxxStrip.restoreStripStyleFlavorInParams(params, flavoredStripStyle);
    params = LinkerMapMode.restoreLinkerMapModeFlavorInParams(params, flavoredLinkerMapMode);
    CxxBinary cxxBinary = new CxxBinary(
        params
            .copyWithDeps(() -> cxxLinkAndCompileRules.deps, params.getExtraDeps())
            .appendExtraDeps(cxxLinkAndCompileRules.executable.getDeps(ruleFinder)),
        resolver,
        ruleFinder,
        cxxLinkAndCompileRules.getBinaryRule(),
        cxxLinkAndCompileRules.executable,
        args.frameworks,
        args.tests,
        params.getBuildTarget().withoutFlavors(cxxPlatforms.getFlavors()));
    resolver.addToIndex(cxxBinary);
    return cxxBinary;
  }

  @Override
  public Iterable<BuildTarget> findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      Arg constructorArg) {
    ImmutableSet.Builder<BuildTarget> deps = ImmutableSet.builder();

    deps.addAll(findDepsForTargetFromConstructorArgs(buildTarget));

    constructorArg.depsQuery.ifPresent(
        depsQuery ->
            QueryUtils.extractParseTimeTargets(buildTarget, cellRoots, depsQuery)
                .forEach(deps::add));

    return deps.build();
  }

  public Iterable<BuildTarget> findDepsForTargetFromConstructorArgs(BuildTarget buildTarget) {
    ImmutableSet.Builder<BuildTarget> deps = ImmutableSet.builder();

    // Get any parse time deps from the C/C++ platforms.
    deps.addAll(
        CxxPlatforms.getParseTimeDeps(
            cxxPlatforms
                .getValue(buildTarget.getFlavors()).orElse(defaultCxxPlatform)));

    return deps.build();
  }

  @Override
  public Optional<ImmutableSet<FlavorDomain<?>>> flavorDomains() {
    return
        Optional.of(
            ImmutableSet.of(
                // Missing: CXX Compilation Database
                // Missing: CXX Description Enhancer
                // Missing: CXX Infer Enhancer
                cxxPlatforms,
                LinkerMapMode.FLAVOR_DOMAIN,
                StripStyle.FLAVOR_DOMAIN
            )
        );
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> inputFlavors) {
    Set<Flavor> flavors = inputFlavors;

    Set<Flavor> platformFlavors = Sets.intersection(flavors, cxxPlatforms.getFlavors());
    if (platformFlavors.size() > 1) {
      return false;
    }
    flavors = Sets.difference(flavors, platformFlavors);

    flavors = Sets.difference(
        flavors,
        ImmutableSet.of(
            CxxDescriptionEnhancer.HEADER_SYMLINK_TREE_FLAVOR,
            CxxCompilationDatabase.COMPILATION_DATABASE,
            CxxCompilationDatabase.UBER_COMPILATION_DATABASE,
            CxxInferEnhancer.InferFlavors.INFER.get(),
            CxxInferEnhancer.InferFlavors.INFER_ANALYZE.get(),
            CxxInferEnhancer.InferFlavors.INFER_CAPTURE_ALL.get(),
            StripStyle.ALL_SYMBOLS.getFlavor(),
            StripStyle.DEBUGGING_SYMBOLS.getFlavor(),
            StripStyle.NON_GLOBAL_SYMBOLS.getFlavor(),
            LinkerMapMode.NO_LINKER_MAP.getFlavor()));

    return flavors.isEmpty();
  }

  public FlavorDomain<CxxPlatform> getCxxPlatforms() {
    return cxxPlatforms;
  }

  public CxxPlatform getDefaultCxxPlatform() {
    return defaultCxxPlatform;
  }

  @Override
  public <A extends Arg, U> Optional<U> createMetadata(
      BuildTarget buildTarget,
      BuildRuleResolver resolver,
      A args,
      final Class<U> metadataClass) throws NoSuchBuildTargetException {
    if (!metadataClass.isAssignableFrom(CxxCompilationDatabaseDependencies.class) ||
        !buildTarget.getFlavors().contains(CxxCompilationDatabase.COMPILATION_DATABASE)) {
      return Optional.empty();
    }
    return CxxDescriptionEnhancer
        .createCompilationDatabaseDependencies(buildTarget, cxxPlatforms, resolver, args).map(
            metadataClass::cast);
  }

  @Override
  public ImmutableSortedSet<Flavor> addImplicitFlavors(
      ImmutableSortedSet<Flavor> argDefaultFlavors) {
    return addImplicitFlavorsForRuleTypes(argDefaultFlavors, Description.getBuildRuleType(this));
  }

  public ImmutableSortedSet<Flavor> addImplicitFlavorsForRuleTypes(
      ImmutableSortedSet<Flavor> argDefaultFlavors,
      BuildRuleType... types) {
    Optional<Flavor> platformFlavor = getCxxPlatforms().getFlavor(argDefaultFlavors);

    for (BuildRuleType type : types) {
      ImmutableMap<String, Flavor> libraryDefaults =
          cxxBuckConfig.getDefaultFlavorsForRuleType(type);

      if (!platformFlavor.isPresent()) {
        platformFlavor = Optional.ofNullable(
            libraryDefaults.get(CxxBuckConfig.DEFAULT_FLAVOR_PLATFORM));
      }
    }

    if (platformFlavor.isPresent()) {
      return ImmutableSortedSet.of(platformFlavor.get());
    } else {
      // To avoid changing the output path of binaries built without a flavor,
      // we'll default to no flavor, which implicitly builds the default platform.
      return ImmutableSortedSet.of();
    }
  }

  @Override
  public boolean isVersionRoot(ImmutableSet<Flavor> flavors) {
    return true;
  }

  @SuppressFieldNotInitialized
  public static class Arg extends LinkableCxxConstructorArg {
    public Optional<Query> depsQuery = Optional.empty();
  }

}
