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

import static com.facebook.buck.rust.RustCompileUtils.ruleToCrateName;

import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPlatforms;
import com.facebook.buck.cxx.Linker;
import com.facebook.buck.cxx.NativeLinkable;
import com.facebook.buck.cxx.NativeLinkableInput;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.model.HasTests;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.AbstractDescriptionArg;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.Hint;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.ToolProvider;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.versions.VersionPropagator;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.util.List;
import java.util.Map;
import java.util.Optional;


public class RustLibraryDescription implements
    Description<RustLibraryDescription.Arg>,
    ImplicitDepsInferringDescription<RustLibraryDescription.Arg>,
    Flavored,
    VersionPropagator<RustLibraryDescription.Arg> {

  private static final FlavorDomain<RustDescriptionEnhancer.Type> LIBRARY_TYPE =
      FlavorDomain.from("Rust Library Type", RustDescriptionEnhancer.Type.class);
  private final FlavorDomain<CxxPlatform> cxxPlatforms;

  private final RustBuckConfig rustBuckConfig;
  private final CxxPlatform defaultCxxPlatform;

  public RustLibraryDescription(
      RustBuckConfig rustBuckConfig,
      FlavorDomain<CxxPlatform> cxxPlatforms,
      CxxPlatform defaultCxxPlatform) {
    this.rustBuckConfig = rustBuckConfig;
    this.cxxPlatforms = cxxPlatforms;
    this.defaultCxxPlatform = defaultCxxPlatform;
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
    final BuildTarget buildTarget = params.getBuildTarget();
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);

    ImmutableList.Builder<String> rustcArgs = ImmutableList.builder();

    RustCompileUtils.addFeatures(buildTarget, args.features, rustcArgs);

    rustcArgs.addAll(args.rustcFlags);
    rustcArgs.addAll(rustBuckConfig.getRustLibraryFlags());

    String crate = args.crate.orElse(ruleToCrateName(params.getBuildTarget().getShortName()));

    Optional<SourcePath> rootModule = RustCompileUtils.getCrateRoot(
        pathResolver,
        crate,
        args.crateRoot,
        ImmutableSet.of("lib.rs"),
        args.srcs.stream());

    if (!rootModule.isPresent()) {
      throw new HumanReadableException(
          "Can't find suitable top-level source file for %s",
          params.getBuildTarget().getShortName());
    }

    // See if we're building a particular "type" and "platform" of this library, and if so, extract
    // them from the flavors attached to the build target.
    Optional<Map.Entry<Flavor, RustDescriptionEnhancer.Type>> type =
        LIBRARY_TYPE.getFlavorAndValue(buildTarget);
    Optional<CxxPlatform> cxxPlatform = cxxPlatforms.getValue(buildTarget);

    if (type.isPresent()) {
      // Uncommon case - someone explicitly invoked buck to build a specific flavor as the
      // direct target.
      CrateType crateType = type.get().getValue().getCrateType();
      Linker.LinkableDepType depType;

      if (crateType.isDynamic()) {
        depType = Linker.LinkableDepType.SHARED;
      } else {
        if (crateType.isPic()) {
          depType = Linker.LinkableDepType.STATIC_PIC;
        } else {
          depType = Linker.LinkableDepType.STATIC;
        }
      }

      return RustCompileUtils.requireBuild(
          crate,
          params,
          resolver,
          pathResolver,
          ruleFinder,
          cxxPlatform.orElse(defaultCxxPlatform),
          rustBuckConfig,
          rustcArgs.build(),
          /* linkerArgs */ ImmutableList.of(),
          /* linkerInputs */ ImmutableList.of(),
          crateType,
          depType,
          args.srcs,
          rootModule.get());
    }

    // Common case - we're being invoked to satisfy some other rule's dependency.
    return new RustLibrary(params, pathResolver) {
      // RustLinkable
      @Override
      public com.facebook.buck.rules.args.Arg getLinkerArg(
          boolean direct,
          CxxPlatform cxxPlatform, Linker.LinkableDepType depType) {
        BuildRule rule;
        CrateType crateType;

        // Determine a crate type from preferred linkage and deptype.
        // NOTE: DYLIB requires upstream rustc bug #38795 to be fixed, because otherwise
        // the use of -rpath will break with flavored paths containing ','.
        switch (args.preferredLinkage) {
          case ANY:
          default:
            switch (depType) {
              case SHARED:
                crateType = CrateType.DYLIB;
                break;
              case STATIC_PIC:
                crateType = CrateType.RLIB_PIC;
                break;
              case STATIC:
              default:
                crateType = CrateType.RLIB;
                break;
            }
            break;

          case SHARED:
            crateType = CrateType.DYLIB;
            break;

          case STATIC:
            if (depType == Linker.LinkableDepType.STATIC) {
              crateType = CrateType.RLIB;
            } else {
              crateType = CrateType.RLIB_PIC;
            }
            break;
        }

        try {
          rule = RustCompileUtils.requireBuild(
              crate,
              params,
              resolver,
              getResolver(),
              ruleFinder,
              cxxPlatform,
              rustBuckConfig,
              rustcArgs.build(),
              /* linkerArgs */ ImmutableList.of(),
              /* linkerInputs */ ImmutableList.of(),
              crateType,
              depType,
              args.srcs,
              rootModule.get());
        } catch (NoSuchBuildTargetException e) {
          throw new RuntimeException(e);
        }
        SourcePath rlib = new BuildTargetSourcePath(rule.getBuildTarget());
        return new RustLibraryArg(pathResolver, crate, rlib, direct, params.getDeps());
      }

      @Override
      public Linkage getPreferredLinkage() {
        return args.preferredLinkage;
      }

      @Override
      public ImmutableMap<String, SourcePath> getRustSharedLibraries(CxxPlatform cxxPlatform)
          throws NoSuchBuildTargetException {
        ImmutableMap.Builder<String, SourcePath> libs = ImmutableMap.builder();
        String sharedLibrarySoname = CrateType.DYLIB.filenameFor(crate, cxxPlatform);
        BuildRule sharedLibraryBuildRule =
            RustCompileUtils.requireBuild(
                crate,
                params,
                resolver,
                getResolver(),
                ruleFinder,
                cxxPlatform,
                rustBuckConfig,
                rustcArgs.build(),
                /* linkerArgs */ ImmutableList.of(),
                /* linkerInputs */ ImmutableList.of(),
                CrateType.DYLIB,
                Linker.LinkableDepType.SHARED,
                args.srcs,
                rootModule.get());
        libs.put(
            sharedLibrarySoname,
            new BuildTargetSourcePath(sharedLibraryBuildRule.getBuildTarget()));
        return libs.build();
      }

      // NativeLinkable
      @Override
      public Iterable<? extends NativeLinkable> getNativeLinkableDeps() {
        return ImmutableList.of();
      }

      @Override
      public Iterable<? extends NativeLinkable> getNativeLinkableExportedDeps() {
        return FluentIterable.from(getDeps())
            .filter(NativeLinkable.class);
      }

      @Override
      public NativeLinkableInput getNativeLinkableInput(
          CxxPlatform cxxPlatform,
          Linker.LinkableDepType depType)
          throws NoSuchBuildTargetException {
        CrateType crateType;

        switch (depType) {
          case SHARED:
            crateType = CrateType.CDYLIB;
            break;

          case STATIC_PIC:
            crateType = CrateType.STATIC_PIC;
            break;

          case STATIC:
          default:
            crateType = CrateType.STATIC;
            break;
        }

        BuildRule rule = RustCompileUtils.requireBuild(
            crate,
            params,
            resolver,
            getResolver(),
            ruleFinder,
            cxxPlatform,
            rustBuckConfig,
            rustcArgs.build(),
              /* linkerArgs */ ImmutableList.of(),
              /* linkerInputs */ ImmutableList.of(),
            crateType,
            depType,
            args.srcs,
            rootModule.get());

        SourcePath lib = new BuildTargetSourcePath(rule.getBuildTarget());
        SourcePathArg arg = new SourcePathArg(getResolver(), lib);

        return NativeLinkableInput.builder().addArgs(arg).build();
      }

      @Override
      public Linkage getPreferredLinkage(CxxPlatform cxxPlatform) {
        return args.preferredLinkage;
      }

      @Override
      public ImmutableMap<String, SourcePath> getSharedLibraries(CxxPlatform cxxPlatform)
          throws NoSuchBuildTargetException {
        ImmutableMap.Builder<String, SourcePath> libs = ImmutableMap.builder();
        String sharedLibrarySoname =
            CxxDescriptionEnhancer.getSharedLibrarySoname(
                Optional.empty(),
                getBuildTarget(),
                cxxPlatform);
        BuildRule sharedLibraryBuildRule =
            RustCompileUtils.requireBuild(
                crate,
                params,
                resolver,
                getResolver(),
                ruleFinder,
                cxxPlatform,
                rustBuckConfig,
                rustcArgs.build(),
              /* linkerArgs */ ImmutableList.of(),
              /* linkerInputs */ ImmutableList.of(),
                CrateType.CDYLIB,
                Linker.LinkableDepType.SHARED,
                args.srcs,
                rootModule.get());
        libs.put(
            sharedLibrarySoname,
            new BuildTargetSourcePath(sharedLibraryBuildRule.getBuildTarget()));
        return libs.build();
      }
    };
  }

  @Override
  public Iterable<BuildTarget> findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      Arg constructorArg) {
    ImmutableSet.Builder<BuildTarget> deps = ImmutableSet.builder();

    deps.addAll(rustBuckConfig.getRustCompiler().getParseTimeDeps());
    deps.addAll(
        rustBuckConfig.getLinker()
            .map(ToolProvider::getParseTimeDeps)
            .orElse(ImmutableList.of()));

    deps.addAll(CxxPlatforms.getParseTimeDeps(cxxPlatforms.getValues()));

    return deps.build();
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    if (cxxPlatforms.containsAnyOf(flavors)) {
      return true;
    }

    for (RustDescriptionEnhancer.Type type : RustDescriptionEnhancer.Type.values()) {
      if (flavors.contains(type.getFlavor())) {
        return true;
      }
    }

    return false;
  }

  @Override
  public Optional<ImmutableSet<FlavorDomain<?>>> flavorDomains() {
    return Optional.of(ImmutableSet.of(cxxPlatforms, LIBRARY_TYPE));
  }

  @SuppressFieldNotInitialized
  public static class Arg extends AbstractDescriptionArg implements HasTests {
    public ImmutableSortedSet<SourcePath> srcs = ImmutableSortedSet.of();
    public ImmutableSortedSet<String> features = ImmutableSortedSet.of();
    public List<String> rustcFlags = ImmutableList.of();
    public ImmutableSortedSet<BuildTarget> deps = ImmutableSortedSet.of();
    public NativeLinkable.Linkage preferredLinkage = NativeLinkable.Linkage.ANY;
    public Optional<String> crate;
    public Optional<SourcePath> crateRoot;
    @Hint(isDep = false)
    public ImmutableSortedSet<BuildTarget> tests = ImmutableSortedSet.of();

    @Override
    public ImmutableSortedSet<BuildTarget> getTests() {
      return tests;
    }
  }
}
