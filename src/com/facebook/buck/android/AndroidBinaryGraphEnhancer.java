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

package com.facebook.buck.android;

import com.facebook.buck.android.AndroidBinary.ExopackageMode;
import com.facebook.buck.android.AndroidBinary.PackageType;
import com.facebook.buck.android.AndroidBinary.RelinkerMode;
import com.facebook.buck.android.FilterResourcesStep.ResourceFilter;
import com.facebook.buck.android.NdkCxxPlatforms.TargetCpuType;
import com.facebook.buck.android.ResourcesFilter.ResourceCompressionMode;
import com.facebook.buck.android.aapt.RDotTxtEntry.RType;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.jvm.java.DefaultJavaLibrary;
import com.facebook.buck.jvm.java.JavaLibrary;
import com.facebook.buck.jvm.java.JavaLibraryRules;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.jvm.java.JavacOptionsAmender;
import com.facebook.buck.jvm.java.JavacToJarStepFactory;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRules;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.coercer.BuildConfigFields;
import com.facebook.buck.rules.coercer.ManifestEntries;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class AndroidBinaryGraphEnhancer {

  public static final Flavor DEX_FLAVOR = ImmutableFlavor.of("dex");
  public static final Flavor DEX_MERGE_FLAVOR = ImmutableFlavor.of("dex_merge");
  public static final Flavor RESOURCES_FILTER_FLAVOR = ImmutableFlavor.of("resources_filter");
  public static final Flavor AAPT_PACKAGE_FLAVOR = ImmutableFlavor.of("aapt_package");
  private static final Flavor CALCULATE_ABI_FLAVOR = ImmutableFlavor.of("calculate_exopackage_abi");
  public static final Flavor PACKAGE_STRING_ASSETS_FLAVOR =
      ImmutableFlavor.of("package_string_assets");
  private static final Flavor TRIM_UBER_R_DOT_JAVA_FLAVOR =
      ImmutableFlavor.of("trim_uber_r_dot_java");
  private static final Flavor COMPILE_UBER_R_DOT_JAVA_FLAVOR =
      ImmutableFlavor.of("compile_uber_r_dot_java");
  private static final Flavor DEX_UBER_R_DOT_JAVA_FLAVOR =
      ImmutableFlavor.of("dex_uber_r_dot_java");
  private static final Flavor GENERATE_NATIVE_LIB_MERGE_MAP_GENERATED_CODE_FLAVOR =
      ImmutableFlavor.of("generate_native_lib_merge_map_generated_code");
  private static final Flavor COMPILE_NATIVE_LIB_MERGE_MAP_GENERATED_CODE_FLAVOR =
      ImmutableFlavor.of("compile_native_lib_merge_map_generated_code");

  private final BuildTarget originalBuildTarget;
  private final ImmutableSortedSet<BuildRule> originalDeps;
  private final BuildRuleParams buildRuleParams;
  private final boolean trimResourceIds;
  private final Optional<String> keepResourcePattern;
  private final Optional<BuildTarget> nativeLibraryMergeCodeGenerator;
  private final ManifestEntries manifestEntries;
  private final BuildRuleResolver ruleResolver;
  private final SourcePathResolver pathResolver;
  private final SourcePathRuleFinder ruleFinder;
  private final ResourceCompressionMode resourceCompressionMode;
  private final ResourceFilter resourceFilter;
  private final EnumSet<RType> bannedDuplicateResourceTypes;
  private final Optional<String> resourceUnionPackage;
  private final ImmutableSet<String> locales;
  private final SourcePath manifest;
  private final PackageType packageType;
  private final boolean shouldBuildStringSourceMap;
  private final boolean shouldPreDex;
  private final Path primaryDexPath;
  private final DexSplitMode dexSplitMode;
  private final ImmutableSet<BuildTarget> buildTargetsToExcludeFromDex;
  private final ImmutableSet<BuildTarget> resourcesToExclude;
  private final boolean skipCrunchPngs;
  private final boolean includesVectorDrawables;
  private final JavacOptions javacOptions;
  private final EnumSet<ExopackageMode> exopackageModes;
  private final BuildConfigFields buildConfigValues;
  private final Optional<SourcePath> buildConfigValuesFile;
  private final Optional<Integer> xzCompressionLevel;
  private final AndroidNativeLibsPackageableGraphEnhancer nativeLibsEnhancer;
  private final APKModuleGraph apkModuleGraph;
  private final ListeningExecutorService dxExecutorService;

  AndroidBinaryGraphEnhancer(
      BuildRuleParams originalParams,
      BuildRuleResolver ruleResolver,
      ResourceCompressionMode resourceCompressionMode,
      ResourceFilter resourcesFilter,
      EnumSet<RType> bannedDuplicateResourceTypes,
      Optional<String> resourceUnionPackage,
      ImmutableSet<String> locales,
      SourcePath manifest,
      PackageType packageType,
      ImmutableSet<TargetCpuType> cpuFilters,
      boolean shouldBuildStringSourceMap,
      boolean shouldPreDex,
      Path primaryDexPath,
      DexSplitMode dexSplitMode,
      ImmutableSet<BuildTarget> buildTargetsToExcludeFromDex,
      ImmutableSet<BuildTarget> resourcesToExclude,
      boolean skipCrunchPngs,
      boolean includesVectorDrawables,
      JavacOptions javacOptions,
      EnumSet<ExopackageMode> exopackageModes,
      BuildConfigFields buildConfigValues,
      Optional<SourcePath> buildConfigValuesFile,
      Optional<Integer> xzCompressionLevel,
      boolean trimResourceIds,
      Optional<String> keepResourcePattern,
      ImmutableMap<TargetCpuType, NdkCxxPlatform> nativePlatforms,
      Optional<Map<String, List<Pattern>>> nativeLibraryMergeMap,
      Optional<BuildTarget> nativeLibraryMergeGlue,
      Optional<BuildTarget> nativeLibraryMergeCodeGenerator,
      RelinkerMode relinkerMode,
      ListeningExecutorService dxExecutorService,
      ManifestEntries manifestEntries,
      CxxBuckConfig cxxBuckConfig,
      APKModuleGraph apkModuleGraph) {
    this.buildRuleParams = originalParams;
    this.manifestEntries = manifestEntries;
    this.originalBuildTarget = originalParams.getBuildTarget();
    this.originalDeps = originalParams.getDeps();
    this.ruleResolver = ruleResolver;
    this.ruleFinder = new SourcePathRuleFinder(ruleResolver);
    this.pathResolver = new SourcePathResolver(ruleFinder);
    this.resourceCompressionMode = resourceCompressionMode;
    this.resourceFilter = resourcesFilter;
    this.bannedDuplicateResourceTypes = bannedDuplicateResourceTypes;
    this.resourceUnionPackage = resourceUnionPackage;
    this.locales = locales;
    this.manifest = manifest;
    this.packageType = packageType;
    this.shouldBuildStringSourceMap = shouldBuildStringSourceMap;
    this.shouldPreDex = shouldPreDex;
    this.primaryDexPath = primaryDexPath;
    this.dexSplitMode = dexSplitMode;
    this.buildTargetsToExcludeFromDex = buildTargetsToExcludeFromDex;
    this.resourcesToExclude = resourcesToExclude;
    this.skipCrunchPngs = skipCrunchPngs;
    this.includesVectorDrawables = includesVectorDrawables;
    this.javacOptions = javacOptions;
    this.exopackageModes = exopackageModes;
    this.buildConfigValues = buildConfigValues;
    this.buildConfigValuesFile = buildConfigValuesFile;
    this.dxExecutorService = dxExecutorService;
    this.xzCompressionLevel = xzCompressionLevel;
    this.trimResourceIds = trimResourceIds;
    this.keepResourcePattern = keepResourcePattern;
    this.nativeLibraryMergeCodeGenerator = nativeLibraryMergeCodeGenerator;
    this.nativeLibsEnhancer =
        new AndroidNativeLibsPackageableGraphEnhancer(
            ruleResolver,
            originalParams,
            nativePlatforms,
            cpuFilters,
            cxxBuckConfig,
            nativeLibraryMergeMap,
            nativeLibraryMergeGlue,
            relinkerMode,
            apkModuleGraph);
    this.apkModuleGraph = apkModuleGraph;
  }

  AndroidGraphEnhancementResult createAdditionalBuildables() throws NoSuchBuildTargetException {
    ImmutableSortedSet.Builder<BuildRule> enhancedDeps = ImmutableSortedSet.naturalOrder();
    enhancedDeps.addAll(originalDeps);

    ImmutableList.Builder<BuildRule> additionalJavaLibrariesBuilder = ImmutableList.builder();

    AndroidPackageableCollector collector =
        new AndroidPackageableCollector(
            originalBuildTarget,
            buildTargetsToExcludeFromDex,
            resourcesToExclude,
            apkModuleGraph);
    collector.addPackageables(AndroidPackageableCollector.getPackageableRules(originalDeps));
    AndroidPackageableCollection packageableCollection = collector.build();
    AndroidPackageableCollection.ResourceDetails resourceDetails =
        packageableCollection.getResourceDetails();

    AndroidNativeLibsGraphEnhancementResult nativeLibsEnhancementResult =
        nativeLibsEnhancer.enhance(packageableCollection);
    Optional<ImmutableMap<APKModule, CopyNativeLibraries>> copyNativeLibraries =
        nativeLibsEnhancementResult.getCopyNativeLibraries();
    if (copyNativeLibraries.isPresent()) {
      ruleResolver.addAllToIndex(copyNativeLibraries.get().values());
      enhancedDeps.addAll(copyNativeLibraries.get().values());
    }

    Optional<ImmutableSortedMap<String, String>> sonameMergeMap =
        nativeLibsEnhancementResult.getSonameMergeMap();
    if (sonameMergeMap.isPresent() && nativeLibraryMergeCodeGenerator.isPresent()) {
      BuildRule generatorRule = ruleResolver.getRule(nativeLibraryMergeCodeGenerator.get());

      BuildTarget writeMapTarget = createBuildTargetWithFlavor(
          GENERATE_NATIVE_LIB_MERGE_MAP_GENERATED_CODE_FLAVOR);
      GenerateCodeForMergedLibraryMap generateCodeForMergedLibraryMap =
          new GenerateCodeForMergedLibraryMap(
              buildRuleParams.copyWithChanges(
                  writeMapTarget,
                  /* declaredDeps */ Suppliers.ofInstance(ImmutableSortedSet.of(generatorRule)),
                  /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of())),
                  sonameMergeMap.get(),
                  generatorRule);
      ruleResolver.addToIndex(generateCodeForMergedLibraryMap);

      BuildTarget compileMergedNativeLibGenCode =
          createBuildTargetWithFlavor(COMPILE_NATIVE_LIB_MERGE_MAP_GENERATED_CODE_FLAVOR);
      BuildRuleParams paramsForCompileGenCode = buildRuleParams.copyWithChanges(
          compileMergedNativeLibGenCode,
          Suppliers.ofInstance(ImmutableSortedSet.of(generateCodeForMergedLibraryMap)),
          /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of()));
      DefaultJavaLibrary compileMergedNativeLibMapGenCode = new DefaultJavaLibrary(
          paramsForCompileGenCode,
          pathResolver,
          ruleFinder,
          ImmutableSet.of(generateCodeForMergedLibraryMap.getSourcePathToOutput()),
          /* resources */ ImmutableSet.of(),
          javacOptions.getGeneratedSourceFolderName(),
          /* proguardConfig */ Optional.empty(),
          /* postprocessClassesCommands */ ImmutableList.of(),
          /* exportedDeps */ ImmutableSortedSet.of(),
          /* providedDeps */ ImmutableSortedSet.of(),
          JavaLibraryRules.getAbiInputs(ruleResolver, paramsForCompileGenCode.getDeps()),
          /* trackClassUsage */ false,
          /* additionalClasspathEntries */ ImmutableSet.of(),
          new JavacToJarStepFactory(
              // Kind of a hack: override language level to 7 to allow string switch.
              // This can be removed once no one who uses this feature sets the level
              // to 6 in their .buckconfig.
              javacOptions.withSourceLevel("7").withTargetLevel("7"),
              JavacOptionsAmender.IDENTITY),
          /* resourcesRoot */ Optional.empty(),
          /* manifest file */ Optional.empty(),
          /* mavenCoords */ Optional.empty(),
          ImmutableSortedSet.of(),
          /* classesToRemoveFromJar */ ImmutableSet.of());
      ruleResolver.addToIndex(compileMergedNativeLibMapGenCode);
      additionalJavaLibrariesBuilder.add(compileMergedNativeLibMapGenCode);
      enhancedDeps.add(compileMergedNativeLibMapGenCode);
    }

    ImmutableSortedSet<BuildRule> resourceRules =
        getTargetsAsRules(resourceDetails.getResourcesWithNonEmptyResDir());

    ImmutableCollection<BuildRule> rulesWithResourceDirectories =
        ruleFinder.filterBuildRuleInputs(resourceDetails.getResourceDirectories());

    FilteredResourcesProvider filteredResourcesProvider;
    boolean needsResourceFiltering = resourceFilter.isEnabled() ||
        resourceCompressionMode.isStoreStringsAsAssets() ||
        !locales.isEmpty();

    if (needsResourceFiltering) {
      BuildRuleParams paramsForResourcesFilter =
          buildRuleParams.copyWithChanges(
              createBuildTargetWithFlavor(RESOURCES_FILTER_FLAVOR),
              Suppliers.ofInstance(
                  ImmutableSortedSet.<BuildRule>naturalOrder()
                      .addAll(resourceRules)
                      .addAll(rulesWithResourceDirectories)
                      .build()),
              /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of()));
      ResourcesFilter resourcesFilter = new ResourcesFilter(
          paramsForResourcesFilter,
          resourceDetails.getResourceDirectories(),
          ImmutableSet.copyOf(resourceDetails.getWhitelistedStringDirectories()),
          locales,
          resourceCompressionMode,
          resourceFilter);
      ruleResolver.addToIndex(resourcesFilter);

      filteredResourcesProvider = resourcesFilter;
      enhancedDeps.add(resourcesFilter);
      resourceRules = ImmutableSortedSet.of(resourcesFilter);
    } else {
      filteredResourcesProvider = new IdentityResourcesProvider(
          resourceDetails.getResourceDirectories().stream()
              .map(pathResolver::getRelativePath)
              .collect(MoreCollectors.toImmutableList()));
    }

    // Create the AaptPackageResourcesBuildable.
    BuildTarget buildTargetForAapt = createBuildTargetWithFlavor(AAPT_PACKAGE_FLAVOR);
    BuildRuleParams paramsForAaptPackageResources = buildRuleParams.copyWithChanges(
        buildTargetForAapt,
        Suppliers.ofInstance(ImmutableSortedSet.of()),
        Suppliers.ofInstance(ImmutableSortedSet.of()));
    AaptPackageResources aaptPackageResources = new AaptPackageResources(
        paramsForAaptPackageResources,
        ruleFinder,
        ruleResolver,
        manifest,
        filteredResourcesProvider,
        getTargetsAsResourceDeps(resourceDetails.getResourcesWithNonEmptyResDir()),
        getTargetsAsRules(resourceDetails.getResourcesWithEmptyResButNonEmptyAssetsDir()),
        packageableCollection.getAssetsDirectories(),
        resourceUnionPackage,
        packageType,
        shouldBuildStringSourceMap,
        skipCrunchPngs,
        includesVectorDrawables,
        bannedDuplicateResourceTypes,
        manifestEntries);
    ruleResolver.addToIndex(aaptPackageResources);
    enhancedDeps.add(aaptPackageResources);

    Optional<PackageStringAssets> packageStringAssets = Optional.empty();
    if (resourceCompressionMode.isStoreStringsAsAssets()) {
      BuildTarget buildTargetForPackageStringAssets =
          createBuildTargetWithFlavor(PACKAGE_STRING_ASSETS_FLAVOR);
      BuildRuleParams paramsForPackageStringAssets = buildRuleParams.copyWithChanges(
          buildTargetForPackageStringAssets,
          Suppliers.ofInstance(
              ImmutableSortedSet.<BuildRule>naturalOrder()
                  .add(aaptPackageResources)
                  .addAll(resourceRules)
                  .addAll(rulesWithResourceDirectories)
                  // Model the dependency on the presence of res directories, which, in the case
                  // of resource filtering, is cached by the `ResourcesFilter` rule.
                  .addAll(
                      Iterables.filter(
                          ImmutableList.of(filteredResourcesProvider),
                          BuildRule.class))
                  .build()),
          /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of()));
      packageStringAssets = Optional.of(
          new PackageStringAssets(
              paramsForPackageStringAssets,
              locales,
              filteredResourcesProvider,
              aaptPackageResources));
      ruleResolver.addToIndex(packageStringAssets.get());
      enhancedDeps.add(packageStringAssets.get());
    }

    // BuildConfig deps should not be added for instrumented APKs because BuildConfig.class has
    // already been added to the APK under test.
    if (packageType != PackageType.INSTRUMENTED) {
      addBuildConfigDeps(
          packageableCollection,
          enhancedDeps,
          additionalJavaLibrariesBuilder);
    }

    ImmutableList<BuildRule> additionalJavaLibraries = additionalJavaLibrariesBuilder.build();
    ImmutableMultimap<APKModule, DexProducedFromJavaLibrary> preDexedLibraries =
        ImmutableMultimap.of();
    if (shouldPreDex) {
      preDexedLibraries = createPreDexRulesForLibraries(
          // TODO(dreiss): Put R.java here.
          additionalJavaLibraries,
          packageableCollection);
    }

    // Create rule to trim uber R.java sources.
    Collection<DexProducedFromJavaLibrary> preDexedLibrariesForResourceIdFiltering =
        trimResourceIds ?
            preDexedLibraries.values() :
            ImmutableList.of();
    BuildRuleParams paramsForTrimUberRDotJava = buildRuleParams.copyWithChanges(
        createBuildTargetWithFlavor(TRIM_UBER_R_DOT_JAVA_FLAVOR),
        Suppliers.ofInstance(ImmutableSortedSet.<BuildRule>naturalOrder()
            .add(aaptPackageResources)
            .addAll(preDexedLibrariesForResourceIdFiltering)
            .build()),
        /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of()));
    TrimUberRDotJava trimUberRDotJava = new TrimUberRDotJava(
        paramsForTrimUberRDotJava,
        aaptPackageResources,
        preDexedLibrariesForResourceIdFiltering,
        keepResourcePattern);
    ruleResolver.addToIndex(trimUberRDotJava);

    // Create rule to compile uber R.java sources.
    BuildTarget compileUberRDotJavaTarget =
        createBuildTargetWithFlavor(COMPILE_UBER_R_DOT_JAVA_FLAVOR);
    BuildRuleParams paramsForCompileUberRDotJava = buildRuleParams.copyWithChanges(
        compileUberRDotJavaTarget,
        Suppliers.ofInstance(ImmutableSortedSet.of(trimUberRDotJava)),
        /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of()));
    JavaLibrary compileUberRDotJava = new DefaultJavaLibrary(
        paramsForCompileUberRDotJava,
        pathResolver,
        ruleFinder,
        ImmutableSet.of(trimUberRDotJava.getSourcePathToOutput()),
        /* resources */ ImmutableSet.of(),
        javacOptions.getGeneratedSourceFolderName(),
        /* proguardConfig */ Optional.empty(),
        /* postprocessClassesCommands */ ImmutableList.of(),
        /* exportedDeps */ ImmutableSortedSet.of(),
        /* providedDeps */ ImmutableSortedSet.of(),
        // Because the Uber R.java has no method bodies or private methods or fields,
        // we can just use its output as the ABI.
        JavaLibraryRules.getAbiInputs(ruleResolver, paramsForCompileUberRDotJava.getDeps()),
        /* trackClassUsage */ false,
        /* additionalClasspathEntries */ ImmutableSet.of(),
        new JavacToJarStepFactory(
            javacOptions.withSourceLevel("7").withTargetLevel("7"),
            JavacOptionsAmender.IDENTITY
        ),
        /* resourcesRoot */ Optional.empty(),
        /* manifest file */ Optional.empty(),
        /* mavenCoords */ Optional.empty(),
        ImmutableSortedSet.of(),
        /* classesToRemoveFromJar */ ImmutableSet.of());
    ruleResolver.addToIndex(compileUberRDotJava);

    // Create rule to dex uber R.java sources.
    BuildRuleParams paramsForDexUberRDotJava = buildRuleParams.copyWithChanges(
        createBuildTargetWithFlavor(DEX_UBER_R_DOT_JAVA_FLAVOR),
        Suppliers.ofInstance(ImmutableSortedSet.of(compileUberRDotJava)),
        /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of()));
    DexProducedFromJavaLibrary dexUberRDotJava =
        new DexProducedFromJavaLibrary(paramsForDexUberRDotJava, compileUberRDotJava);
    ruleResolver.addToIndex(dexUberRDotJava);

    Optional<PreDexMerge> preDexMerge = Optional.empty();
    if (shouldPreDex) {
      preDexMerge = Optional.of(createPreDexMergeRule(
              preDexedLibraries,
              dexUberRDotJava));
      enhancedDeps.add(preDexMerge.get());
    } else {
      enhancedDeps.addAll(getTargetsAsRules(packageableCollection.getJavaLibrariesToDex()));
      // If not pre-dexing, AndroidBinary needs to ProGuard and/or dex the compiled R.java.
      enhancedDeps.add(compileUberRDotJava);
    }

    // Add dependencies on all the build rules generating third-party JARs.  This is mainly to
    // correctly capture deps when a prebuilt_jar forwards the output from another build rule.
    enhancedDeps.addAll(
        ruleFinder.filterBuildRuleInputs(packageableCollection.getPathsToThirdPartyJars()));

    Optional<ComputeExopackageDepsAbi> computeExopackageDepsAbi = Optional.empty();
    if (!exopackageModes.isEmpty()) {
      BuildRuleParams paramsForComputeExopackageAbi = buildRuleParams.copyWithChanges(
          createBuildTargetWithFlavor(CALCULATE_ABI_FLAVOR),
          Suppliers.ofInstance(enhancedDeps.build()),
        /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of()));
      computeExopackageDepsAbi = Optional.of(
          new ComputeExopackageDepsAbi(
              paramsForComputeExopackageAbi,
              exopackageModes,
              packageableCollection,
              copyNativeLibraries,
              preDexMerge
          ));
      ruleResolver.addToIndex(computeExopackageDepsAbi.get());
      enhancedDeps.add(computeExopackageDepsAbi.get());
    }

    return AndroidGraphEnhancementResult.builder()
        .setPackageableCollection(packageableCollection)
        .setAaptPackageResources(aaptPackageResources)
        .setCompiledUberRDotJava(compileUberRDotJava)
        .setCopyNativeLibraries(copyNativeLibraries)
        .setPackageStringAssets(packageStringAssets)
        .setPreDexMerge(preDexMerge)
        .setComputeExopackageDepsAbi(computeExopackageDepsAbi)
        .setClasspathEntriesToDex(
            ImmutableSet.<SourcePath>builder()
                .addAll(packageableCollection.getClasspathEntriesToDex())
                .addAll(
                    additionalJavaLibraries.stream()
                        .map(BuildRule::getSourcePathToOutput)
                        .collect(MoreCollectors.toImmutableList()))
                .build())
        .setFinalDeps(enhancedDeps.build())
        .setAPKModuleGraph(apkModuleGraph)
        .build();
  }

  /**
   * If the user specified any android_build_config() rules, then we must add some build rules to
   * generate the production {@code BuildConfig.class} files and ensure that they are included in
   * the list of {@link AndroidPackageableCollection#getClasspathEntriesToDex}.
   */
  private void addBuildConfigDeps(
      AndroidPackageableCollection packageableCollection,
      ImmutableSortedSet.Builder<BuildRule> enhancedDeps,
      ImmutableList.Builder<BuildRule> compilationRulesBuilder) throws NoSuchBuildTargetException {
    BuildConfigFields buildConfigConstants = BuildConfigFields.fromFields(
        ImmutableList.of(
            BuildConfigFields.Field.of(
                "boolean",
                BuildConfigs.DEBUG_CONSTANT,
                String.valueOf(packageType != AndroidBinary.PackageType.RELEASE)),
            BuildConfigFields.Field.of(
                "boolean",
                BuildConfigs.IS_EXO_CONSTANT,
                String.valueOf(!exopackageModes.isEmpty())),
            BuildConfigFields.Field.of(
                "int",
                BuildConfigs.EXOPACKAGE_FLAGS,
                String.valueOf(ExopackageMode.toBitmask(exopackageModes)))));
    for (Map.Entry<String, BuildConfigFields> entry :
        packageableCollection.getBuildConfigs().entrySet()) {
      // Merge the user-defined constants with the APK-specific overrides.
      BuildConfigFields totalBuildConfigValues = BuildConfigFields.empty()
          .putAll(entry.getValue())
          .putAll(buildConfigValues)
          .putAll(buildConfigConstants);

      // Each enhanced dep needs a unique build target, so we parameterize the build target by the
      // Java package.
      String javaPackage = entry.getKey();
      Flavor flavor = ImmutableFlavor.of("buildconfig_" + javaPackage.replace('.', '_'));
      BuildRuleParams buildConfigParams = new BuildRuleParams(
          createBuildTargetWithFlavor(flavor),
          /* declaredDeps */ Suppliers.ofInstance(ImmutableSortedSet.of()),
          /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of()),
          buildRuleParams.getProjectFilesystem(),
          buildRuleParams.getCellRoots());
      JavaLibrary buildConfigJavaLibrary = AndroidBuildConfigDescription.createBuildRule(
          buildConfigParams,
          javaPackage,
          totalBuildConfigValues,
          buildConfigValuesFile,
          /* useConstantExpressions */ true,
          javacOptions,
          ruleResolver);
      ruleResolver.addToIndex(buildConfigJavaLibrary);

      enhancedDeps.add(buildConfigJavaLibrary);
      Preconditions.checkNotNull(
          buildConfigJavaLibrary.getSourcePathToOutput(),
          "%s must have an output file.",
          buildConfigJavaLibrary);
      compilationRulesBuilder.add(buildConfigJavaLibrary);
    }
  }

  /**
   * Creates/finds the set of build rules that correspond to pre-dex'd artifacts that should be
   * merged to create the final classes.dex for the APK.
   * <p>
   * This method may modify {@code ruleResolver}, inserting new rules into its index.
   */
  @VisibleForTesting
  PreDexMerge createPreDexMergeRule(
      ImmutableMultimap<APKModule, DexProducedFromJavaLibrary> allPreDexDeps,
      DexProducedFromJavaLibrary dexForUberRDotJava) {
    BuildRuleParams paramsForPreDexMerge = buildRuleParams.copyWithChanges(
        createBuildTargetWithFlavor(DEX_MERGE_FLAVOR),
        Suppliers.ofInstance(
            ImmutableSortedSet.<BuildRule>naturalOrder()
                .addAll(getDexMergeDeps(
                    dexForUberRDotJava,
                    ImmutableSet.copyOf(allPreDexDeps.values())))
                .build()),
        /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of()));
    PreDexMerge preDexMerge = new PreDexMerge(
        paramsForPreDexMerge,
        primaryDexPath,
        dexSplitMode,
        apkModuleGraph,
        allPreDexDeps,
        dexForUberRDotJava,
        dxExecutorService,
        xzCompressionLevel);
    ruleResolver.addToIndex(preDexMerge);

    return preDexMerge;
  }

  @VisibleForTesting
  ImmutableMultimap<APKModule, DexProducedFromJavaLibrary> createPreDexRulesForLibraries(
      Iterable<BuildRule> additionalJavaLibrariesToDex,
      AndroidPackageableCollection packageableCollection) {
    Iterable<BuildTarget> additionalJavaLibraryTargets =
        FluentIterable.from(additionalJavaLibrariesToDex).transform(
            BuildRule::getBuildTarget);
    ImmutableMultimap.Builder<APKModule, DexProducedFromJavaLibrary> preDexDeps =
        ImmutableMultimap.builder();
    for (BuildTarget buildTarget : Iterables.concat(
        packageableCollection.getJavaLibrariesToDex(),
        additionalJavaLibraryTargets)) {
      Preconditions.checkState(
          !buildTargetsToExcludeFromDex.contains(buildTarget),
          "JavaLibrary should have been excluded from target to dex: %s", buildTarget);

      BuildRule libraryRule = ruleResolver.getRule(buildTarget);

      Preconditions.checkState(libraryRule instanceof JavaLibrary);
      JavaLibrary javaLibrary = (JavaLibrary) libraryRule;

      // If the rule has no output file (which happens when a java_library has no srcs or
      // resources, but export_deps is true), then there will not be anything to dx.
      if (javaLibrary.getSourcePathToOutput() == null) {
        continue;
      }

      // See whether the corresponding IntermediateDexRule has already been added to the
      // ruleResolver.
      BuildTarget originalTarget = javaLibrary.getBuildTarget();
      BuildTarget preDexTarget = BuildTarget.builder(originalTarget)
          .addFlavors(DEX_FLAVOR)
          .build();
      Optional<BuildRule> preDexRule = ruleResolver.getRuleOptional(preDexTarget);
      if (preDexRule.isPresent()) {
        preDexDeps.put(
            apkModuleGraph.findModuleForTarget(buildTarget),
            (DexProducedFromJavaLibrary) preDexRule.get());
        continue;
      }

      // Create the IntermediateDexRule and add it to both the ruleResolver and preDexDeps.
      BuildRuleParams paramsForPreDex = buildRuleParams.copyWithChanges(
          preDexTarget,
          Suppliers.ofInstance(
              ImmutableSortedSet.of(ruleResolver.getRule(javaLibrary.getBuildTarget()))),
          /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of()));
      DexProducedFromJavaLibrary preDex =
          new DexProducedFromJavaLibrary(paramsForPreDex, javaLibrary);
      ruleResolver.addToIndex(preDex);
      preDexDeps.put(apkModuleGraph.findModuleForTarget(buildTarget), preDex);
    }
    return preDexDeps.build();
  }

  private BuildTarget createBuildTargetWithFlavor(Flavor flavor) {
    return BuildTarget.builder(originalBuildTarget)
        .addFlavors(flavor)
        .build();
  }

  private ImmutableSortedSet<BuildRule> getDexMergeDeps(
      DexProducedFromJavaLibrary dexForUberRDotJava,
      ImmutableSet<DexProducedFromJavaLibrary> preDexDeps) {
    ImmutableSet.Builder<BuildTarget> targets = ImmutableSet.builder();
    targets.add(dexForUberRDotJava.getBuildTarget());
    for (DexProducedFromJavaLibrary preDex : preDexDeps) {
      targets.add(preDex.getBuildTarget());
    }
    return getTargetsAsRules(targets.build());
  }

  private ImmutableSortedSet<BuildRule> getTargetsAsRules(Collection<BuildTarget> buildTargets) {
    return BuildRules.toBuildRulesFor(
        originalBuildTarget,
        ruleResolver,
        buildTargets);
  }

  private ImmutableList<HasAndroidResourceDeps> getTargetsAsResourceDeps(
      Collection<BuildTarget> targets) {
    return getTargetsAsRules(targets).stream()
        .map(input -> {
          Preconditions.checkState(input instanceof HasAndroidResourceDeps);
          return (HasAndroidResourceDeps) input;
        })
        .collect(MoreCollectors.toImmutableList());
  }
}
