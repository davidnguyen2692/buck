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

import com.facebook.buck.android.AndroidPackageable;
import com.facebook.buck.android.AndroidPackageableCollector;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.Pair;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.NoopBuildRule;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.FileListableLinkerInputArg;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An action graph representation of a C/C++ library from the target graph, providing the
 * various interfaces to make it consumable by C/C++ preprocessing and native linkable rules.
 */
public class CxxLibrary
    extends NoopBuildRule
    implements AbstractCxxLibrary, HasRuntimeDeps, NativeTestable, NativeLinkTarget {

  private final BuildRuleParams params;
  private final BuildRuleResolver ruleResolver;
  private final SourcePathResolver pathResolver;
  private final Iterable<? extends BuildRule> exportedDeps;
  private final Predicate<CxxPlatform> hasExportedHeaders;
  private final Predicate<CxxPlatform> headerOnly;
  private final Function<? super CxxPlatform, ImmutableMultimap<CxxSource.Type, String>>
      exportedPreprocessorFlags;
  private final Function<? super CxxPlatform, Iterable<? extends Arg>> exportedLinkerFlags;
  private final Function<? super CxxPlatform, NativeLinkableInput> linkTargetInput;
  private final Optional<Pattern> supportedPlatformsRegex;
  private final ImmutableSet<FrameworkPath> frameworks;
  private final ImmutableSet<FrameworkPath> libraries;
  private final Linkage linkage;
  private final boolean linkWhole;
  private final Optional<String> soname;
  private final ImmutableSortedSet<BuildTarget> tests;
  private final boolean canBeAsset;
  /**
   * Whether Native Linkable dependencies should be propagated for the purpose of computing objects
   * to link at link time. Setting this to false makes this library invisible to linking, so it and
   * its link-time dependencies are ignored.
   */
  private final boolean propagateLinkables;

  private final Map<Pair<Flavor, Linker.LinkableDepType>, NativeLinkableInput> nativeLinkableCache =
      new HashMap<>();

  private final LoadingCache<
          CxxPreprocessables.CxxPreprocessorInputCacheKey,
          ImmutableMap<BuildTarget, CxxPreprocessorInput>
        > transitiveCxxPreprocessorInputCache =
      CxxPreprocessables.getTransitiveCxxPreprocessorInputCache(this);

  public CxxLibrary(
      BuildRuleParams params,
      BuildRuleResolver ruleResolver,
      SourcePathResolver pathResolver,
      Iterable<? extends BuildRule> exportedDeps,
      Predicate<CxxPlatform> hasExportedHeaders,
      Predicate<CxxPlatform> headerOnly,
      Function<? super CxxPlatform, ImmutableMultimap<CxxSource.Type, String>>
          exportedPreprocessorFlags,
      Function<? super CxxPlatform, Iterable<? extends Arg>> exportedLinkerFlags,
      Function<? super CxxPlatform, NativeLinkableInput> linkTargetInput,
      Optional<Pattern> supportedPlatformsRegex,
      ImmutableSet<FrameworkPath> frameworks,
      ImmutableSet<FrameworkPath> libraries,
      Linkage linkage,
      boolean linkWhole,
      Optional<String> soname,
      ImmutableSortedSet<BuildTarget> tests,
      boolean canBeAsset,
      boolean propagateLinkables) {
    super(params, pathResolver);
    this.params = params;
    this.ruleResolver = ruleResolver;
    this.pathResolver = pathResolver;
    this.exportedDeps = exportedDeps;
    this.hasExportedHeaders = hasExportedHeaders;
    this.headerOnly = headerOnly;
    this.exportedPreprocessorFlags = exportedPreprocessorFlags;
    this.exportedLinkerFlags = exportedLinkerFlags;
    this.linkTargetInput = linkTargetInput;
    this.supportedPlatformsRegex = supportedPlatformsRegex;
    this.frameworks = frameworks;
    this.libraries = libraries;
    this.linkage = linkage;
    this.linkWhole = linkWhole;
    this.soname = soname;
    this.tests = tests;
    this.canBeAsset = canBeAsset;
    this.propagateLinkables = propagateLinkables;
  }

  private boolean isPlatformSupported(CxxPlatform cxxPlatform) {
    return !supportedPlatformsRegex.isPresent() ||
        supportedPlatformsRegex.get()
            .matcher(cxxPlatform.getFlavor().toString())
            .find();
  }

  @Override
  public Iterable<? extends CxxPreprocessorDep> getCxxPreprocessorDeps(CxxPlatform cxxPlatform) {
    if (!isPlatformSupported(cxxPlatform)) {
      return ImmutableList.of();
    }
    return FluentIterable.from(getDeps())
        .filter(CxxPreprocessorDep.class);
  }

  @Override
  public CxxPreprocessorInput getCxxPreprocessorInput(
      CxxPlatform cxxPlatform,
      HeaderVisibility headerVisibility) throws NoSuchBuildTargetException {
    boolean hasHeaderSymlinkTree =
        headerVisibility != HeaderVisibility.PUBLIC || hasExportedHeaders.apply(cxxPlatform);
    return CxxPreprocessables.getCxxPreprocessorInput(
        params,
        ruleResolver,
        hasHeaderSymlinkTree,
        cxxPlatform,
        headerVisibility,
        CxxPreprocessables.IncludeType.LOCAL,
        exportedPreprocessorFlags.apply(cxxPlatform),
        frameworks);
  }

  @Override
  public Optional<HeaderSymlinkTree> getExportedHeaderSymlinkTree(CxxPlatform cxxPlatform) {
    if (hasExportedHeaders.apply(cxxPlatform)) {
      return Optional.of(
          CxxPreprocessables.requireHeaderSymlinkTreeForLibraryTarget(
              ruleResolver,
              getBuildTarget(),
              cxxPlatform.getFlavor()));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public ImmutableMap<BuildTarget, CxxPreprocessorInput> getTransitiveCxxPreprocessorInput(
      CxxPlatform cxxPlatform,
      HeaderVisibility headerVisibility) {
    return transitiveCxxPreprocessorInputCache.getUnchecked(
        ImmutableCxxPreprocessorInputCacheKey.of(cxxPlatform, headerVisibility));
  }

  @Override
  public Iterable<NativeLinkable> getNativeLinkableDeps() {
    if (!propagateLinkables) {
      return ImmutableList.of();
    }
    return FluentIterable.from(getDeclaredDeps())
        .filter(NativeLinkable.class);
  }

  @Override
  public Iterable<NativeLinkable> getNativeLinkableDepsForPlatform(CxxPlatform cxxPlatform) {
    if (!isPlatformSupported(cxxPlatform)) {
      return ImmutableList.of();
    }
    return getNativeLinkableDeps();
  }

  @Override
  public Iterable<? extends NativeLinkable> getNativeLinkableExportedDeps() {
    if (!propagateLinkables) {
      return ImmutableList.of();
    }
    return FluentIterable.from(exportedDeps)
        .filter(NativeLinkable.class);
  }

  @Override
  public Iterable<? extends NativeLinkable> getNativeLinkableExportedDepsForPlatform(
      CxxPlatform cxxPlatform) {
    if (!isPlatformSupported(cxxPlatform)) {
      return ImmutableList.of();
    }
    return getNativeLinkableExportedDeps();
  }

  public NativeLinkableInput getNativeLinkableInputUncached(
      CxxPlatform cxxPlatform,
      Linker.LinkableDepType type) throws NoSuchBuildTargetException {

    if (!isPlatformSupported(cxxPlatform)) {
      return NativeLinkableInput.of();
    }

    // Build up the arguments used to link this library.  If we're linking the
    // whole archive, wrap the library argument in the necessary "ld" flags.
    ImmutableList.Builder<Arg> linkerArgsBuilder = ImmutableList.builder();
    linkerArgsBuilder.addAll(Preconditions.checkNotNull(exportedLinkerFlags.apply(cxxPlatform)));

    if (!headerOnly.apply(cxxPlatform) && propagateLinkables) {
      boolean isStatic;
      switch (linkage) {
        case STATIC:
          isStatic = true;
          break;
        case SHARED:
          isStatic = false;
          break;
        case ANY:
          isStatic = type != Linker.LinkableDepType.SHARED;
          break;
        default:
          throw new IllegalStateException("unhandled linkage type: " + linkage);
      }
      if (isStatic) {
        Archive archive =
            (Archive) requireBuildRule(
                cxxPlatform.getFlavor(),
                type == Linker.LinkableDepType.STATIC ?
                    CxxDescriptionEnhancer.STATIC_FLAVOR :
                    CxxDescriptionEnhancer.STATIC_PIC_FLAVOR);
        if (linkWhole) {
          Linker linker = cxxPlatform.getLd().resolve(ruleResolver);
          linkerArgsBuilder.addAll(linker.linkWhole(archive.toArg()));
        } else {
          Arg libraryArg = archive.toArg();
          if (libraryArg instanceof SourcePathArg) {
            linkerArgsBuilder.add(
                FileListableLinkerInputArg.withSourcePathArg((SourcePathArg) libraryArg));
          } else {
            linkerArgsBuilder.add(libraryArg);
          }
        }
      } else {
        BuildRule rule =
            requireBuildRule(
                cxxPlatform.getFlavor(),
                cxxPlatform.getSharedLibraryInterfaceFactory().isPresent() ?
                    CxxLibraryDescription.Type.SHARED_INTERFACE.getFlavor() :
                    CxxLibraryDescription.Type.SHARED.getFlavor());
        linkerArgsBuilder.add(
            new SourcePathArg(pathResolver, new BuildTargetSourcePath(rule.getBuildTarget())));
      }
    }

    final ImmutableList<Arg> linkerArgs = linkerArgsBuilder.build();

    return NativeLinkableInput.of(
        linkerArgs,
        Preconditions.checkNotNull(frameworks),
        Preconditions.checkNotNull(libraries));
  }

  @Override
  public NativeLinkableInput getNativeLinkableInput(
      CxxPlatform cxxPlatform,
      Linker.LinkableDepType type)
      throws NoSuchBuildTargetException {
    Pair<Flavor, Linker.LinkableDepType> key = new Pair<>(cxxPlatform.getFlavor(), type);
    NativeLinkableInput input = nativeLinkableCache.get(key);
    if (input == null) {
      input = getNativeLinkableInputUncached(cxxPlatform, type);
      nativeLinkableCache.put(key, input);
    }
    return input;
  }

  public BuildRule requireBuildRule(Flavor... flavors) throws NoSuchBuildTargetException {
    return ruleResolver.requireRule(getBuildTarget().withAppendedFlavors(flavors));
  }

  @Override
  public NativeLinkable.Linkage getPreferredLinkage(CxxPlatform cxxPlatform) {
    return linkage;
  }

  @Override
  public Iterable<AndroidPackageable> getRequiredPackageables() {
    return AndroidPackageableCollector.getPackageableRules(params.getDeps());
  }

  @Override
  public void addToCollector(AndroidPackageableCollector collector) {
    if (canBeAsset) {
      collector.addNativeLinkableAsset(this);
    } else {
      collector.addNativeLinkable(this);
    }
  }

  @Override
  public ImmutableMap<String, SourcePath> getSharedLibraries(
      CxxPlatform cxxPlatform) throws NoSuchBuildTargetException {
    if (headerOnly.apply(cxxPlatform)) {
      return ImmutableMap.of();
    }
    if (!isPlatformSupported(cxxPlatform)) {
      return ImmutableMap.of();
    }
    ImmutableMap.Builder<String, SourcePath> libs = ImmutableMap.builder();
    String sharedLibrarySoname = CxxDescriptionEnhancer.getSharedLibrarySoname(
        soname,
        getBuildTarget(),
        cxxPlatform);
    BuildRule sharedLibraryBuildRule = requireBuildRule(
        cxxPlatform.getFlavor(),
        CxxDescriptionEnhancer.SHARED_FLAVOR);
    libs.put(
        sharedLibrarySoname,
        new BuildTargetSourcePath(sharedLibraryBuildRule.getBuildTarget()));
    return libs.build();
  }

  @Override
  public boolean isTestedBy(BuildTarget testTarget) {
    return tests.contains(testTarget);
  }

  @Override
  public NativeLinkTargetMode getNativeLinkTargetMode(CxxPlatform cxxPlatform) {
    return NativeLinkTargetMode.library(
        CxxDescriptionEnhancer.getSharedLibrarySoname(
            soname,
            getBuildTarget(),
            cxxPlatform));
  }

  @Override
  public Iterable<? extends NativeLinkable> getNativeLinkTargetDeps(CxxPlatform cxxPlatform) {
    return Iterables.concat(
        getNativeLinkableDepsForPlatform(cxxPlatform),
        getNativeLinkableExportedDepsForPlatform(cxxPlatform));
  }

  @Override
  public NativeLinkableInput getNativeLinkTargetInput(CxxPlatform cxxPlatform) {
    cxxPlatform.getCompilerDebugPathSanitizer().assertInProjectFilesystem(
        this,
        getProjectFilesystem());
    return linkTargetInput.apply(cxxPlatform);
  }

  @Override
  public Optional<Path> getNativeLinkTargetOutputPath(CxxPlatform cxxPlatform) {
    return Optional.empty();
  }

  @Override
  public Stream<BuildTarget> getRuntimeDeps() {
    // We export all declared deps as runtime deps, to setup a transitive runtime dep chain which
    // will pull in runtime deps (e.g. other binaries) or transitive C/C++ libraries.  Since the
    // `CxxLibrary` rules themselves are noop meta rules, they shouldn't add any unnecessary
    // overhead.
    return Stream
        .concat(
            getDeclaredDeps().stream(),
            StreamSupport.stream(exportedDeps.spliterator(), false))
        .map(BuildRule::getBuildTarget);
  }

  public Iterable<? extends Arg> getExportedLinkerFlags(CxxPlatform cxxPlatform) {
    return exportedLinkerFlags.apply(cxxPlatform);
  }
}
