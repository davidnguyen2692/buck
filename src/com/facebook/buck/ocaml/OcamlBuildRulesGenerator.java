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

package com.facebook.buck.ocaml;

import static com.google.common.base.Preconditions.checkNotNull;

import com.facebook.buck.cxx.Compiler;
import com.facebook.buck.cxx.CxxPreprocessorInput;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A generator of fine-grained OCaml build rules
 */
public class OcamlBuildRulesGenerator {

  private static final Flavor DEBUG_FLAVOR = ImmutableFlavor.of("debug");

  private final BuildRuleParams params;
  private final BuildRuleResolver resolver;
  private final SourcePathResolver pathResolver;
  private final OcamlBuildContext ocamlContext;
  private final ImmutableMap<Path, ImmutableList<Path>> mlInput;
  private final ImmutableList<SourcePath> cInput;

  private final Compiler cCompiler;
  private final Compiler cxxCompiler;
  private final boolean bytecodeOnly;

  private BuildRule cleanRule;

  public OcamlBuildRulesGenerator(
      BuildRuleParams params,
      SourcePathResolver pathResolver,
      BuildRuleResolver resolver,
      OcamlBuildContext ocamlContext,
      ImmutableMap<Path, ImmutableList<Path>> mlInput,
      ImmutableList<SourcePath> cInput,
      Compiler cCompiler,
      Compiler cxxCompiler,
      boolean bytecodeOnly) {
    this.params = params;
    this.pathResolver = pathResolver;
    this.resolver = resolver;
    this.ocamlContext = ocamlContext;
    this.mlInput = mlInput;
    this.cInput = cInput;
    this.cCompiler = cCompiler;
    this.cxxCompiler = cxxCompiler;
    this.bytecodeOnly = bytecodeOnly;
    this.cleanRule = generateCleanBuildRule(params, pathResolver, ocamlContext);
  }

  /**
   * Generates build rules for both the native and bytecode outputs
   */
  OcamlGeneratedBuildRules generate() {

    // TODO(): The order of rules added to "rules" matters - the OcamlRuleBuilder
    // object currently assumes that the native or bytecode compilation rule will
    // be the first one in the list. We should eliminate this restriction.
    ImmutableList.Builder<BuildRule> rules = ImmutableList.builder();
    ImmutableList.Builder<BuildRule> nativeCompileDeps = ImmutableList.builder();
    ImmutableList.Builder<BuildRule> bytecodeCompileDeps = ImmutableList.builder();

    ImmutableList<SourcePath> objFiles = generateCCompilation(cInput);

    if (!this.bytecodeOnly) {
      ImmutableList<SourcePath> cmxFiles = generateMLNativeCompilation(mlInput);
      nativeCompileDeps.addAll(pathResolver.filterBuildRuleInputs(cmxFiles));
      BuildRule nativeLink = generateNativeLinking(
          ImmutableList.<SourcePath>builder()
              .addAll(Iterables.concat(cmxFiles, objFiles))
              .build()
      );
      rules.add(nativeLink);
    }

    ImmutableList<SourcePath> cmoFiles = generateMLBytecodeCompilation(mlInput);
    bytecodeCompileDeps.addAll(pathResolver.filterBuildRuleInputs(cmoFiles));
    BuildRule bytecodeLink = generateBytecodeLinking(
        ImmutableList.<SourcePath>builder()
            .addAll(Iterables.concat(cmoFiles, objFiles))
            .build()
    );
    rules.add(bytecodeLink);

    if (!ocamlContext.isLibrary()) {
      rules.add(generateDebugLauncherRule());
    }

    return OcamlGeneratedBuildRules.builder()
        .setRules(rules.build())
        .setNativeCompileDeps(ImmutableSortedSet.copyOf(nativeCompileDeps.build()))
        .setBytecodeCompileDeps(ImmutableSortedSet.copyOf(bytecodeCompileDeps.build()))
        .setObjectFiles(objFiles)
        .setBytecodeLink(bytecodeLink)
        .build();
  }

  private static String getCOutputName(String name) {
    String base = Files.getNameWithoutExtension(name);
    String ext = Files.getFileExtension(name);
    Preconditions.checkArgument(OcamlCompilables.SOURCE_EXTENSIONS.contains(ext));
    return base + ".o";
  }

  public static BuildTarget createCCompileBuildTarget(
      BuildTarget target,
      String name) {
    return BuildTarget
        .builder(target)
        .addFlavors(
            ImmutableFlavor.of(
                String.format(
                    "compile-%s",
                    getCOutputName(name)
                        .replace('/', '-')
                        .replace('.', '-')
                        .replace('+', '-')
                        .replace(' ', '-'))))
        .build();
  }

  private ImmutableList<SourcePath> generateCCompilation(ImmutableList<SourcePath> cInput) {

    ImmutableList.Builder<SourcePath> objects = ImmutableList.builder();

    ImmutableList.Builder<String> cCompileFlags = ImmutableList.builder();
    cCompileFlags.addAll(ocamlContext.getCCompileFlags());
    cCompileFlags.addAll(ocamlContext.getCommonCFlags());

    CxxPreprocessorInput cxxPreprocessorInput = ocamlContext.getCxxPreprocessorInput();

    for (SourcePath cSrc : cInput) {
      String name = pathResolver.getAbsolutePath(cSrc).toFile().getName();
      BuildTarget target = createCCompileBuildTarget(
          params.getBuildTarget(),
          name);

      BuildRuleParams cCompileParams = params.copyWithChanges(
          target,
        /* declaredDeps */ Suppliers.ofInstance(
              ImmutableSortedSet.<BuildRule>naturalOrder()
                  // Depend on the rule that generates the sources and headers we're compiling.
                  .addAll(pathResolver.filterBuildRuleInputs(cSrc))
                  // Add any deps from the C/C++ preprocessor input.
                  .addAll(cxxPreprocessorInput.getDeps(resolver, pathResolver))
                  // Add the clean rule, to ensure that any shared output folders shared with
                  // OCaml build artifacts are properly cleaned.
                  .add(this.cleanRule)
                  // Add deps from the C compiler, since we're calling it.
                  .addAll(cCompiler.getDeps(pathResolver))
                  .addAll(params.getDeclaredDeps().get())
                  .build()),
        /* extraDeps */ params.getExtraDeps());

      Path outputPath = ocamlContext.getCOutput(pathResolver.getRelativePath(cSrc));
      OcamlCCompile compileRule = new OcamlCCompile(
          cCompileParams,
          pathResolver,
          new OcamlCCompileStep.Args(
              cCompiler.getEnvironment(),
              cCompiler.getCommandPrefix(pathResolver),
              ocamlContext.getOcamlCompiler().get(),
              ocamlContext.getOcamlInteropIncludesDir(),
              outputPath,
              cSrc,
              cCompileFlags.build(),
              cxxPreprocessorInput.getIncludes()));
      resolver.addToIndex(compileRule);
      objects.add(
          new BuildTargetSourcePath(compileRule.getBuildTarget()));
    }
    return objects.build();
  }

  private BuildRule generateCleanBuildRule(
      BuildRuleParams params,
      SourcePathResolver pathResolver,
      OcamlBuildContext ocamlContext) {
    BuildTarget cleanTarget =
      BuildTarget.builder(params.getBuildTarget())
        .addFlavors(
            ImmutableFlavor.of(
                String.format(
                    "clean-%s",
                    params.getBuildTarget().getShortName())))
        .build();

    BuildRuleParams cleanParams = params.copyWithChanges(
      cleanTarget,
      Suppliers.ofInstance(
        ImmutableSortedSet.<BuildRule>naturalOrder()
          .addAll(params.getDeclaredDeps().get())
          .build()),
      params.getExtraDeps());

    BuildRule cleanRule = new OcamlClean(cleanParams, pathResolver, ocamlContext);
    resolver.addToIndex(cleanRule);
    return cleanRule;
  }

  public static BuildTarget addDebugFlavor(BuildTarget target) {
    return BuildTarget.builder(target).addFlavors(DEBUG_FLAVOR).build();
  }

  private BuildRule generateDebugLauncherRule() {
    BuildRuleParams debugParams = params.copyWithChanges(
        addDebugFlavor(params.getBuildTarget()),
        Suppliers.ofInstance(ImmutableSortedSet.of()),
        Suppliers.ofInstance(ImmutableSortedSet.of()));

    OcamlDebugLauncher debugLauncher = new OcamlDebugLauncher(
        debugParams,
        pathResolver,
        new OcamlDebugLauncherStep.Args(
            ocamlContext.getOcamlDebug().get(),
            ocamlContext.getBytecodeOutput(),
            ocamlContext.getOcamlInput(),
            ocamlContext.getBytecodeIncludeFlags()
        )
    );
    resolver.addToIndex(debugLauncher);
    return debugLauncher;
  }

  /**
   * Links the .cmx files generated by the native compilation
   */
  private BuildRule generateNativeLinking(ImmutableList<SourcePath> allInputs) {
    BuildRuleParams linkParams = params.copyWithChanges(
        params.getBuildTarget(),
        Suppliers.ofInstance(
            ImmutableSortedSet.<BuildRule>naturalOrder()
                .addAll(pathResolver.filterBuildRuleInputs(allInputs))
                .addAll(
                    ocamlContext.getNativeLinkableInput().getArgs().stream()
                        .flatMap(arg -> arg.getDeps(pathResolver).stream())
                        .iterator())
                .addAll(
                    ocamlContext.getCLinkableInput().getArgs().stream()
                        .flatMap(arg -> arg.getDeps(pathResolver).stream())
                        .iterator())
                .addAll(cxxCompiler.getDeps(pathResolver))
                .build()),
        Suppliers.ofInstance(
            ImmutableSortedSet.of()));

    ImmutableList.Builder<String> flags = ImmutableList.builder();
    flags.addAll(ocamlContext.getFlags());
    flags.addAll(ocamlContext.getCommonCLinkerFlags());

    OcamlLink link = new OcamlLink(
        linkParams,
        pathResolver,
        allInputs,
        cxxCompiler.getEnvironment(),
        cxxCompiler.getCommandPrefix(pathResolver),
        ocamlContext.getOcamlCompiler().get(),
        flags.build(),
        ocamlContext.getOcamlInteropIncludesDir(),
        ocamlContext.getNativeOutput(),
        ocamlContext.getNativeLinkableInput().getArgs(),
        ocamlContext.getCLinkableInput().getArgs(),
        ocamlContext.isLibrary(),
        /* isBytecode */ false);
    resolver.addToIndex(link);
    return link;
  }

  private static final Flavor BYTECODE_FLAVOR = ImmutableFlavor.of("bytecode");

  public static BuildTarget addBytecodeFlavor(BuildTarget target) {
    return BuildTarget.builder(target).addFlavors(BYTECODE_FLAVOR).build();
  }

  /**
   * Links the .cmo files generated by the bytecode compilation
   */
  private BuildRule generateBytecodeLinking(ImmutableList<SourcePath> allInputs) {
    BuildRuleParams linkParams = params.copyWithChanges(
        addBytecodeFlavor(params.getBuildTarget()),
        Suppliers.ofInstance(
            ImmutableSortedSet.<BuildRule>naturalOrder()
                .addAll(pathResolver.filterBuildRuleInputs(allInputs))
                .addAll(ocamlContext.getBytecodeLinkDeps())
                .addAll(
                    Stream.concat(
                        ocamlContext.getBytecodeLinkableInput().getArgs().stream(),
                        ocamlContext.getCLinkableInput().getArgs().stream())
                        .flatMap(arg -> arg.getDeps(pathResolver).stream())
                        .filter(rule -> !(rule instanceof OcamlBuild))
                        .iterator())
                .addAll(cxxCompiler.getDeps(pathResolver))
                .build()),
    Suppliers.ofInstance(ImmutableSortedSet.of()));

    ImmutableList.Builder<String> flags = ImmutableList.builder();
    flags.addAll(ocamlContext.getFlags());
    flags.addAll(ocamlContext.getCommonCLinkerFlags());

    OcamlLink link = new OcamlLink(
        linkParams,
        pathResolver,
        allInputs,
        cxxCompiler.getEnvironment(),
        cxxCompiler.getCommandPrefix(pathResolver),
        ocamlContext.getOcamlBytecodeCompiler().get(),
        flags.build(),
        ocamlContext.getOcamlInteropIncludesDir(),
        ocamlContext.getBytecodeOutput(),
        ocamlContext.getBytecodeLinkableInput().getArgs(),
        ocamlContext.getCLinkableInput().getArgs(),
        ocamlContext.isLibrary(),
        /* isBytecode */ true);
    resolver.addToIndex(link);
    return link;
  }

  private ImmutableList<String> getCompileFlags(boolean isBytecode, boolean excludeDeps) {
    String output = isBytecode ? ocamlContext.getCompileBytecodeOutputDir().toString() :
        ocamlContext.getCompileNativeOutputDir().toString();
    ImmutableList.Builder<String> flagBuilder = ImmutableList.builder();
    flagBuilder.addAll(ocamlContext.getIncludeFlags(isBytecode,  excludeDeps));
    flagBuilder.addAll(ocamlContext.getFlags());
    flagBuilder.add(
        OcamlCompilables.OCAML_INCLUDE_FLAG,
        output
    );
    return flagBuilder.build();
  }

  /**
   * The native-code executable
   */
  private static String getMLNativeOutputName(String name) {
    String base = Files.getNameWithoutExtension(name);
    String ext = Files.getFileExtension(name);
    Preconditions.checkArgument(
        OcamlCompilables.SOURCE_EXTENSIONS.contains(ext),
        "Unexpected extension: " + ext);
    String dotExt = "." + ext;
    if (dotExt.equals(OcamlCompilables.OCAML_ML) ||
        dotExt.equals(OcamlCompilables.OCAML_RE)) {
      return base + OcamlCompilables.OCAML_CMX;
    } else if (dotExt.equals(OcamlCompilables.OCAML_MLI) ||
        dotExt.equals(OcamlCompilables.OCAML_REI)) {
      return base + OcamlCompilables.OCAML_CMI;
    } else {
      Preconditions.checkState(false, "Unexpected extension: " + ext);
      return base;
    }
  }

  /**
   * The bytecode output (which is also executable)
   */
  private static String getMLBytecodeOutputName(String name) {
    String base = Files.getNameWithoutExtension(name);
    String ext = Files.getFileExtension(name);
    Preconditions.checkArgument(OcamlCompilables.SOURCE_EXTENSIONS.contains(ext));
    String dotExt = "." + ext;
    if (dotExt.equals(OcamlCompilables.OCAML_ML) ||
        dotExt.equals(OcamlCompilables.OCAML_RE)) {
      return base + OcamlCompilables.OCAML_CMO;
    } else if (dotExt.equals(OcamlCompilables.OCAML_MLI) ||
        dotExt.equals(OcamlCompilables.OCAML_REI)) {
      return base + OcamlCompilables.OCAML_CMI;
    } else {
      Preconditions.checkState(false, "Unexpected extension: " + ext);
      return base;
    }
  }

  public static BuildTarget createMLNativeCompileBuildTarget(
      BuildTarget target,
      String name) {
    return BuildTarget
        .builder(target)
        .addFlavors(
            ImmutableFlavor.of(
                String.format(
                    "ml-compile-%s",
                    getMLNativeOutputName(name)
                        .replace('/', '-')
                        .replace('.', '-')
                        .replace('+', '-')
                        .replace(' ', '-'))))
        .build();
  }

  public static BuildTarget createMLBytecodeCompileBuildTarget(
      BuildTarget target,
      String name) {
    return BuildTarget
        .builder(target)
        .addFlavors(
            ImmutableFlavor.of(
                String.format(
                    "ml-bytecode-compile-%s",
                    getMLBytecodeOutputName(name)
                        .replace('/', '-')
                        .replace('.', '-')
                        .replace('+', '-')
                        .replace(' ', '-'))))
        .build();
  }

  ImmutableList<SourcePath> generateMLNativeCompilation(
      ImmutableMap<Path, ImmutableList<Path>> mlSources) {
    ImmutableList.Builder<SourcePath> cmxFiles = ImmutableList.builder();

    final Map<Path, ImmutableSortedSet<BuildRule>> sourceToRule = Maps.newHashMap();

    for (ImmutableMap.Entry<Path, ImmutableList<Path>>
        mlSource : mlSources.entrySet()) {
      generateSingleMLNativeCompilation(
          sourceToRule,
          cmxFiles,
          mlSource.getKey(),
          mlSources,
          ImmutableList.of());
    }
    return cmxFiles.build();
  }

  /**
   * Compiles a single .ml file to a .cmx
   */
  private void generateSingleMLNativeCompilation(
      Map<Path, ImmutableSortedSet<BuildRule>> sourceToRule,
      ImmutableList.Builder<SourcePath> cmxFiles,
      Path mlSource,
      ImmutableMap<Path, ImmutableList<Path>> sources,
      ImmutableList<Path> cycleDetector) {

    ImmutableList<Path> newCycleDetector = ImmutableList.<Path>builder()
        .addAll(cycleDetector)
        .add(mlSource)
        .build();

    if (cycleDetector.contains(mlSource)) {
      throw new HumanReadableException("Dependency cycle detected: %s",
         Joiner.on(" -> ").join(newCycleDetector));
    }

    if (sourceToRule.containsKey(mlSource)) {
      return;
    }

    ImmutableSortedSet.Builder<BuildRule> depsBuilder = ImmutableSortedSet.naturalOrder();
    if (sources.containsKey(mlSource)) {
      for (Path dep : checkNotNull(sources.get(mlSource))) {
        generateSingleMLNativeCompilation(sourceToRule, cmxFiles, dep, sources, newCycleDetector);
        depsBuilder.addAll(checkNotNull(sourceToRule.get(dep)));
      }
    }
    ImmutableSortedSet<BuildRule> deps = depsBuilder.build();

    String name = mlSource.toFile().getName();

    BuildTarget buildTarget = createMLNativeCompileBuildTarget(
        params.getBuildTarget(),
        name);

    BuildRuleParams compileParams = params.copyWithChanges(
        buildTarget,
        Suppliers.ofInstance(
            ImmutableSortedSet.<BuildRule>naturalOrder()
                .addAll(params.getDeclaredDeps().get())
                .add(this.cleanRule)
                .addAll(deps)
                .addAll(ocamlContext.getNativeCompileDeps())
                .addAll(cCompiler.getDeps(pathResolver))
                .build()),
        params.getExtraDeps());

    String outputFileName = getMLNativeOutputName(name);
    Path outputPath = ocamlContext.getCompileNativeOutputDir()
        .resolve(outputFileName);
    final ImmutableList<String> compileFlags = getCompileFlags(
        /* isBytecode */ false,
        /* excludeDeps */ false);
    OcamlMLCompile compile = new OcamlMLCompile(
        compileParams,
        pathResolver,
        new OcamlMLCompileStep.Args(
            params.getProjectFilesystem()::resolve,
            cCompiler.getEnvironment(),
            cCompiler.getCommandPrefix(pathResolver),
            ocamlContext.getOcamlCompiler().get(),
            ocamlContext.getOcamlInteropIncludesDir(),
            outputPath,
            mlSource,
            compileFlags));
    resolver.addToIndex(compile);
    sourceToRule.put(
        mlSource,
        ImmutableSortedSet.<BuildRule>naturalOrder()
            .add(compile)
            .addAll(deps)
            .build());
    if (!outputFileName.endsWith(OcamlCompilables.OCAML_CMI)) {
      cmxFiles.add(
          new BuildTargetSourcePath(compile.getBuildTarget()));
    }
  }

  private ImmutableList<SourcePath> generateMLBytecodeCompilation(
      ImmutableMap<Path, ImmutableList<Path>> mlSources) {
    ImmutableList.Builder<SourcePath> cmoFiles = ImmutableList.builder();

    final Map<Path, ImmutableSortedSet<BuildRule>> sourceToRule = Maps.newHashMap();

    for (ImmutableMap.Entry<Path, ImmutableList<Path>>
        mlSource : mlSources.entrySet()) {
      generateSingleMLBytecodeCompilation(
          sourceToRule,
          cmoFiles,
          mlSource.getKey(),
          mlSources,
          ImmutableList.of());
    }
    return cmoFiles.build();
  }

  /**
   * Compiles a single .ml file to a .cmo
   */
  private void generateSingleMLBytecodeCompilation(
      Map<Path, ImmutableSortedSet<BuildRule>> sourceToRule,
      ImmutableList.Builder<SourcePath> cmoFiles,
      Path mlSource,
      ImmutableMap<Path, ImmutableList<Path>> sources,
      ImmutableList<Path> cycleDetector) {

    ImmutableList<Path> newCycleDetector = ImmutableList.<Path>builder()
        .addAll(cycleDetector)
        .add(mlSource)
        .build();

    if (cycleDetector.contains(mlSource)) {
      throw new HumanReadableException("Dependency cycle detected: %s",
          Joiner.on(" -> ").join(newCycleDetector));
    }
    if (sourceToRule.containsKey(mlSource)) {
      return;
    }

    ImmutableSortedSet.Builder<BuildRule> depsBuilder = ImmutableSortedSet.naturalOrder();
    if (sources.containsKey(mlSource)) {
      for (Path dep : checkNotNull(sources.get(mlSource))) {
        generateSingleMLBytecodeCompilation(
            sourceToRule,
            cmoFiles,
            dep,
            sources,
            newCycleDetector);
        depsBuilder.addAll(checkNotNull(sourceToRule.get(dep)));
      }
    }
    ImmutableSortedSet<BuildRule> deps = depsBuilder.build();

    String name = mlSource.toFile().getName();
    BuildTarget buildTarget = createMLBytecodeCompileBuildTarget(
        params.getBuildTarget(),
        name);

    BuildRuleParams compileParams = params.copyWithChanges(
        buildTarget,
        Suppliers.ofInstance(
            ImmutableSortedSet.<BuildRule>naturalOrder()
                .add(this.cleanRule)
                .addAll(params.getDeclaredDeps().get())
                .addAll(deps)
                .addAll(ocamlContext.getBytecodeCompileDeps())
                .addAll(cCompiler.getDeps(pathResolver))
                .build()),
        params.getExtraDeps());

    String outputFileName = getMLBytecodeOutputName(name);
    Path outputPath = ocamlContext.getCompileBytecodeOutputDir()
        .resolve(outputFileName);
    final ImmutableList<String> compileFlags = getCompileFlags(
        /* isBytecode */ true,
        /* excludeDeps */ false);
    BuildRule compileBytecode = new OcamlMLCompile(
        compileParams,
        pathResolver,
        new OcamlMLCompileStep.Args(
            params.getProjectFilesystem()::resolve,
            cCompiler.getEnvironment(),
            cCompiler.getCommandPrefix(pathResolver),
            ocamlContext.getOcamlBytecodeCompiler().get(),
            ocamlContext.getOcamlInteropIncludesDir(),
            outputPath,
            mlSource,
            compileFlags));
    resolver.addToIndex(compileBytecode);
    sourceToRule.put(
        mlSource,
        ImmutableSortedSet.<BuildRule>naturalOrder()
            .add(compileBytecode)
            .addAll(deps)
            .build());
    if (!outputFileName.endsWith(OcamlCompilables.OCAML_CMI)) {
      cmoFiles.add(
          new BuildTargetSourcePath(compileBytecode.getBuildTarget()));
    }
  }

}
