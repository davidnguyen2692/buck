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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.cxx.CxxLibrary;
import com.facebook.buck.cxx.CxxLibraryBuilder;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPlatformUtils;
import com.facebook.buck.cxx.InferBuckConfig;
import com.facebook.buck.cxx.NativeLinkable;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CommandTool;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.SourceWithFlags;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.rules.coercer.SourceWithFlagsList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

public class ThriftCxxEnhancerTest {

  private static final BuildTarget TARGET = BuildTargetFactory.newInstance("//:test#cpp");
  private static final BuckConfig BUCK_CONFIG = FakeBuckConfig.builder().build();
  private static final ThriftBuckConfig THRIFT_BUCK_CONFIG = new ThriftBuckConfig(BUCK_CONFIG);
  private static final CxxPlatform CXX_PLATFORM = CxxPlatformUtils.build(
      new CxxBuckConfig(BUCK_CONFIG));
  private static final FlavorDomain<CxxPlatform> CXX_PLATFORMS =
      FlavorDomain.of("C/C++ Platform", CXX_PLATFORM);
  private static final CxxLibraryDescription CXX_LIBRARY_DESCRIPTION =
      new CxxLibraryDescription(
          CxxPlatformUtils.DEFAULT_CONFIG,
          CXX_PLATFORM,
          new InferBuckConfig(BUCK_CONFIG),
          CXX_PLATFORMS);
  private static final ThriftCxxEnhancer ENHANCER_CPP =
      new ThriftCxxEnhancer(
          THRIFT_BUCK_CONFIG,
          CXX_LIBRARY_DESCRIPTION,
          /* cpp2 */ false);
  private static final ThriftCxxEnhancer ENHANCER_CPP2 =
      new ThriftCxxEnhancer(
          THRIFT_BUCK_CONFIG,
          CXX_LIBRARY_DESCRIPTION,
          /* cpp2 */ true);

  private static FakeBuildRule createFakeBuildRule(
      String target,
      SourcePathResolver resolver,
      BuildRule... deps) {
    return new FakeBuildRule(
        new FakeBuildRuleParamsBuilder(BuildTargetFactory.newInstance(target))
            .setDeclaredDeps(ImmutableSortedSet.copyOf(deps))
            .build(),
        resolver);
  }

  private static ThriftCompiler createFakeThriftCompiler(String target) {
    return new ThriftCompiler(
        new FakeBuildRuleParamsBuilder(BuildTargetFactory.newInstance(target)).build(),
        new CommandTool.Builder()
            .addArg(new StringArg("compiler"))
            .build(),
        ImmutableList.of(),
        Paths.get("output"),
        new FakeSourcePath("source"),
        "language",
        ImmutableSet.of(),
        ImmutableList.of(),
        ImmutableSet.of(),
        ImmutableMap.of(),
        ImmutableSortedSet.of());
  }

  @Test
  public void getLanguage() {
    assertEquals(
        "cpp",
        ENHANCER_CPP.getLanguage());
    assertEquals(
        "cpp2",
        ENHANCER_CPP2.getLanguage());
  }

  @Test
  public void getFlavor() {
    assertEquals(
        ImmutableFlavor.of("cpp"),
        ENHANCER_CPP.getFlavor());
    assertEquals(
        ImmutableFlavor.of("cpp2"),
        ENHANCER_CPP2.getFlavor());
  }

  @Test
  public void getCompiler() {
    assertEquals(
        ThriftLibraryDescription.CompilerType.THRIFT,
        ENHANCER_CPP.getCompilerType());
    assertEquals(
        ThriftLibraryDescription.CompilerType.THRIFT2,
        ENHANCER_CPP2.getCompilerType());
  }

  private ImmutableSet<String> getExpectedOptions(BuildTarget target, ImmutableSet<String> opts) {
    return ImmutableSet.<String>builder()
        .addAll(opts)
        .add(String.format("include_prefix=%s", target.getBasePath()))
        .build();
  }

  @Test
  public void getOptions() {
    ThriftConstructorArg arg = new ThriftConstructorArg();
    ImmutableSet<String> options;

    // Test empty options.
    options = ImmutableSet.of();
    arg.cppOptions = options;
    assertEquals(
        getExpectedOptions(TARGET, options),
        ENHANCER_CPP.getOptions(TARGET, arg));
    arg.cpp2Options = options;
    assertEquals(
        getExpectedOptions(TARGET, options),
        ENHANCER_CPP2.getOptions(TARGET, arg));

    // Test set options.
    options = ImmutableSet.of("test", "option");
    arg.cppOptions = options;
    assertEquals(
        getExpectedOptions(TARGET, options),
        ENHANCER_CPP.getOptions(TARGET, arg));
    arg.cpp2Options = options;
    assertEquals(
        getExpectedOptions(TARGET, options),
        ENHANCER_CPP.getOptions(TARGET, arg));

    // Test absent options.
    arg.cppOptions = ImmutableSet.of();
    assertEquals(
        getExpectedOptions(TARGET, ImmutableSet.of()),
        ENHANCER_CPP.getOptions(TARGET, arg));
    arg.cpp2Options = ImmutableSet.of();
    assertEquals(
        getExpectedOptions(TARGET, ImmutableSet.of()),
        ENHANCER_CPP2.getOptions(TARGET, arg));
  }

  private void expectImplicitDeps(
      ThriftCxxEnhancer enhancer,
      ImmutableSet<String> options,
      ImmutableSet<BuildTarget> expected) {

    ThriftConstructorArg arg = new ThriftConstructorArg();
    arg.cppOptions = options;
    arg.cpp2Options = options;

    assertEquals(
        expected,
        enhancer.getImplicitDepsForTargetFromConstructorArg(TARGET, arg));
  }

  @Test
  public void getImplicitDeps() {
    // Setup an enhancer which sets all appropriate values in the config.
    ImmutableMap<String, BuildTarget> config = ImmutableMap.of(
        "cpp_library", BuildTargetFactory.newInstance("//:cpp_library"),
        "cpp2_library", BuildTargetFactory.newInstance("//:cpp2_library"),
        "cpp_reflection_library", BuildTargetFactory.newInstance("//:cpp_reflection_library"),
        "cpp_frozen_library", BuildTargetFactory.newInstance("//:cpp_froze_library"),
        "cpp_json_library", BuildTargetFactory.newInstance("//:cpp_json_library"));
    ImmutableMap.Builder<String, String> strConfig = ImmutableMap.builder();
    for (ImmutableMap.Entry<String, BuildTarget> ent : config.entrySet()) {
      strConfig.put(ent.getKey(), ent.getValue().toString());
    }
    BuckConfig buckConfig = FakeBuckConfig.builder().setSections(
        ImmutableMap.of("thrift", strConfig.build())).build();
    ThriftBuckConfig thriftBuckConfig = new ThriftBuckConfig(buckConfig);
    ThriftCxxEnhancer cppEnhancerWithSettings = new ThriftCxxEnhancer(
        thriftBuckConfig,
        CXX_LIBRARY_DESCRIPTION,
        /* cpp2 */ false);
    ThriftCxxEnhancer cpp2EnhancerWithSettings = new ThriftCxxEnhancer(
        thriftBuckConfig,
        CXX_LIBRARY_DESCRIPTION,
        /* cpp2 */ true);

    // With no options we just need to find the C/C++ thrift library.
    expectImplicitDeps(
        cppEnhancerWithSettings,
        ImmutableSet.of(),
        ImmutableSet.of(
            config.get("cpp_library"),
            config.get("cpp_reflection_library")));
    expectImplicitDeps(
        cpp2EnhancerWithSettings,
        ImmutableSet.of(),
        ImmutableSet.of(
            config.get("cpp2_library"),
            config.get("cpp_reflection_library")));

    // Now check for correct reaction to the "bootstrap" option.
    expectImplicitDeps(
        cppEnhancerWithSettings,
        ImmutableSet.of("bootstrap"),
        ImmutableSet.of());
    expectImplicitDeps(
        cpp2EnhancerWithSettings,
        ImmutableSet.of("bootstrap"),
        ImmutableSet.of());

    // Check the "frozen2" option
    expectImplicitDeps(
        cppEnhancerWithSettings,
        ImmutableSet.of("frozen2"),
        ImmutableSet.of(
            config.get("cpp_library"),
            config.get("cpp_reflection_library"),
            config.get("cpp_frozen_library")));
    expectImplicitDeps(
        cpp2EnhancerWithSettings,
        ImmutableSet.of("frozen2"),
        ImmutableSet.of(
            config.get("cpp2_library"),
            config.get("cpp_reflection_library"),
            config.get("cpp_frozen_library")));

    // Check the "json" option
    expectImplicitDeps(
        cppEnhancerWithSettings,
        ImmutableSet.of("json"),
        ImmutableSet.of(
            config.get("cpp_library"),
            config.get("cpp_reflection_library"),
            config.get("cpp_json_library")));
    expectImplicitDeps(
        cpp2EnhancerWithSettings,
        ImmutableSet.of("json"),
        ImmutableSet.of(
            config.get("cpp2_library"),
            config.get("cpp_reflection_library"),
            config.get("cpp_json_library")));

    // Check the "compatibility" option
    expectImplicitDeps(
        cppEnhancerWithSettings,
        ImmutableSet.of("compatibility"),
        ImmutableSet.of(
            config.get("cpp_library"),
            config.get("cpp_reflection_library")));
    expectImplicitDeps(
        cpp2EnhancerWithSettings,
        ImmutableSet.of("compatibility"),
        ImmutableSet.of(
            TARGET,
            config.get("cpp_library"),
            config.get("cpp_reflection_library"),
            config.get("cpp2_library")));
  }

  @Test
  public void getGeneratedSources() {

    // Test with no options.
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "test_reflection.h",
            "test_reflection.cpp",
            "Test.h",
            "Test.cpp"),
        ENHANCER_CPP.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of()));
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "test_types.tcc",
            "test_types_custom_protocol.h",
            "Test.h",
            "Test.cpp",
            "Test_client.cpp",
            "Test_custom_protocol.h",
            "Test.tcc"),
        ENHANCER_CPP2.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of()));

    // Test with "frozen" option.
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "test_layouts.h",
            "test_layouts.cpp",
            "test_reflection.h",
            "test_reflection.cpp",
            "Test.h",
            "Test.cpp"),
        ENHANCER_CPP.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of("frozen2")));
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "test_types.tcc",
            "test_layouts.h",
            "test_layouts.cpp",
            "test_types_custom_protocol.h",
            "Test.h",
            "Test.cpp",
            "Test_client.cpp",
            "Test_custom_protocol.h",
            "Test.tcc"),
        ENHANCER_CPP2.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of("frozen2")));

    // Test with "bootstrap" option.
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "Test.h",
            "Test.cpp"),
        ENHANCER_CPP.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of("bootstrap")));
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "test_types.tcc",
            "test_types_custom_protocol.h",
            "Test.h",
            "Test.cpp",
            "Test_client.cpp",
            "Test_custom_protocol.h",
            "Test.tcc"),
        ENHANCER_CPP2.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of("bootstrap")));

    // Test with "templates" option.
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "test_types.tcc",
            "test_reflection.h",
            "test_reflection.cpp",
            "Test.h",
            "Test.cpp",
            "Test.tcc"),
        ENHANCER_CPP.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of("templates")));
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "test_types.tcc",
            "test_types_custom_protocol.h",
            "Test.h",
            "Test.cpp",
            "Test_client.cpp",
            "Test_custom_protocol.h",
            "Test.tcc"),
        ENHANCER_CPP2.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of("templates")));

    // Test with "perfhash" option.
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "test_reflection.h",
            "test_reflection.cpp",
            "Test.h",
            "Test.cpp",
            "Test_gperf.tcc"),
        ENHANCER_CPP.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of("perfhash")));
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "test_types.tcc",
            "test_types_custom_protocol.h",
            "Test.h",
            "Test.cpp",
            "Test_client.cpp",
            "Test_custom_protocol.h",
            "Test.tcc"),
        ENHANCER_CPP2.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of("perfhash")));

    // Test with "separate_processmap" option.
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "test_reflection.h",
            "test_reflection.cpp",
            "Test.h",
            "Test.cpp"),
        ENHANCER_CPP.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of("separate_processmap")));
    assertEquals(
        ImmutableSortedSet.of(
            "test_constants.h",
            "test_constants.cpp",
            "test_types.h",
            "test_types.cpp",
            "test_types.tcc",
            "test_types_custom_protocol.h",
            "Test.h",
            "Test.cpp",
            "Test_client.cpp",
            "Test_custom_protocol.h",
            "Test_processmap_binary.cpp",
            "Test_processmap_compact.cpp",
            "Test.tcc"),
        ENHANCER_CPP2.getGeneratedSources(
            "test.thrift",
            ImmutableList.of("Test"),
            ImmutableSet.of("separate_processmap")));
  }

  @Test
  public void createBuildRule() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    BuildRuleParams flavoredParams = new FakeBuildRuleParamsBuilder(TARGET).build();

    // Add a dummy dependency to the constructor arg to make sure it gets through.
    BuildRule argDep = createFakeBuildRule("//:arg_dep", pathResolver);
    resolver.addToIndex(argDep);
    ThriftConstructorArg arg = new ThriftConstructorArg();
    arg.cppHeaderNamespace = Optional.empty();
    arg.cppExportedHeaders = SourceList.EMPTY;
    arg.cppSrcs = SourceWithFlagsList.EMPTY;
    arg.cpp2Options = ImmutableSet.of();
    arg.cpp2Deps = ImmutableSortedSet.of(argDep.getBuildTarget());

    ThriftCompiler thrift1 = createFakeThriftCompiler("//:thrift_source1");
    resolver.addToIndex(thrift1);
    ThriftCompiler thrift2 = createFakeThriftCompiler("//:thrift_source2");
    resolver.addToIndex(thrift2);

    // Setup up some thrift inputs to pass to the createBuildRule method.
    ImmutableMap<String, ThriftSource> sources = ImmutableMap.of(
        "test1.thrift", new ThriftSource(
            thrift1,
            ImmutableList.of(),
            Paths.get("output1")),
        "test2.thrift", new ThriftSource(
            thrift2,
            ImmutableList.of(),
            Paths.get("output2")));

    // Create a dummy implicit dep to pass in.
    CxxLibrary dep =
        (CxxLibrary) new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:dep"))
            .build(resolver);
    ImmutableSortedSet<BuildRule> deps = ImmutableSortedSet.of(dep);

    // Run the enhancer to create the language specific build rule.
    CxxLibrary rule =
        (CxxLibrary) ENHANCER_CPP2.createBuildRule(
            TargetGraph.EMPTY,
            flavoredParams,
            resolver,
            arg,
            sources,
            deps);

    assertThat(
        ImmutableList.copyOf(rule.getNativeLinkableExportedDepsForPlatform(CXX_PLATFORM)),
        Matchers.<NativeLinkable>hasItem(dep));
  }

  @Test
  public void cppSrcsAndHeadersArePropagated() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    BuildRuleParams flavoredParams = new FakeBuildRuleParamsBuilder(TARGET).build();

    final String cppHeaderNamespace = "foo";
    final ImmutableSortedMap<String, SourcePath> cppHeaders =
        ImmutableSortedMap.of(
            "header.h", new FakeSourcePath("header.h"));
    final ImmutableSortedMap<String, SourceWithFlags> cppSrcs =
        ImmutableSortedMap.of(
            "source.cpp", SourceWithFlags.of(new FakeSourcePath("source.cpp")));

    ThriftConstructorArg arg = new ThriftConstructorArg();
    arg.cppOptions = ImmutableSet.of();
    arg.cppDeps = ImmutableSortedSet.of();
    arg.cppHeaderNamespace = Optional.of(cppHeaderNamespace);
    arg.cppExportedHeaders = SourceList.ofNamedSources(cppHeaders);
    arg.cppSrcs = SourceWithFlagsList.ofNamedSources(cppSrcs);

    ThriftCompiler thrift = createFakeThriftCompiler("//:thrift_source");
    resolver.addToIndex(thrift);

    // Setup up some thrift inputs to pass to the createBuildRule method.
    ImmutableMap<String, ThriftSource> sources = ImmutableMap.of(
        "test.thrift", new ThriftSource(
            thrift,
            ImmutableList.of(),
            Paths.get("output")));

    // Run the enhancer with a modified C++ description which checks that appropriate args are
    // propagated.
    CxxLibraryDescription cxxLibraryDescription =
        new CxxLibraryDescription(
            CxxPlatformUtils.DEFAULT_CONFIG,
            CXX_PLATFORM,
            new InferBuckConfig(BUCK_CONFIG),
            CXX_PLATFORMS) {
          @Override
          public <A extends Arg> BuildRule createBuildRule(
              TargetGraph targetGraph,
              BuildRuleParams params,
              BuildRuleResolver resolver,
              A args) throws NoSuchBuildTargetException {
            assertThat(args.headerNamespace, Matchers.equalTo(Optional.of(cppHeaderNamespace)));
            for (Map.Entry<String, SourcePath> header : cppHeaders.entrySet()) {
              assertThat(
                  args.exportedHeaders.getNamedSources().get().get(header.getKey()),
                  Matchers.equalTo(header.getValue()));
            }
            for (Map.Entry<String, SourceWithFlags> source : cppSrcs.entrySet()) {
              assertThat(
                  args.srcs,
                  Matchers.hasItem(source.getValue()));
            }
            return super.createBuildRule(targetGraph, params, resolver, args);
          }
        };
    ThriftCxxEnhancer enhancer =
        new ThriftCxxEnhancer(
            THRIFT_BUCK_CONFIG,
            cxxLibraryDescription,
          /* cpp2 */ false);
    enhancer.createBuildRule(
        TargetGraph.EMPTY,
        flavoredParams,
        resolver,
        arg,
        sources,
        ImmutableSortedSet.of());
  }

  @Test
  public void cppCompileFlagsArePropagated() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    BuildRuleParams flavoredParams = new FakeBuildRuleParamsBuilder(TARGET).build();

    final ImmutableList<String> compilerFlags = ImmutableList.of("-flag");

    ThriftConstructorArg arg = new ThriftConstructorArg();
    arg.cppOptions = ImmutableSet.of();
    arg.cppDeps = ImmutableSortedSet.of();
    arg.cpp2Deps = ImmutableSortedSet.of();
    arg.cpp2CompilerFlags = compilerFlags;
    arg.cppCompilerFlags = compilerFlags;
    arg.cppHeaderNamespace = Optional.empty();
    arg.cppExportedHeaders = SourceList.EMPTY;
    arg.cppSrcs = SourceWithFlagsList.EMPTY;

    ThriftCompiler thrift = createFakeThriftCompiler("//:thrift_source");
    resolver.addToIndex(thrift);

    // Run the enhancer with a modified C++ description which checks that appropriate args are
    // propagated.
    CxxLibraryDescription cxxLibraryDescription =
        new CxxLibraryDescription(
            CxxPlatformUtils.DEFAULT_CONFIG,
            CXX_PLATFORM,
            new InferBuckConfig(BUCK_CONFIG),
            CXX_PLATFORMS) {
          @Override
          public <A extends Arg> BuildRule createBuildRule(
              TargetGraph targetGraph,
              BuildRuleParams params,
              BuildRuleResolver resolver,
              A args) throws NoSuchBuildTargetException {
            assertThat(args.compilerFlags, Matchers.hasItem("-flag"));
            return super.createBuildRule(targetGraph, params, resolver, args);
          }
        };
    for (boolean cpp2 : ImmutableList.of(true, false)) {
      ThriftCxxEnhancer enhancer =
          new ThriftCxxEnhancer(
              THRIFT_BUCK_CONFIG,
              cxxLibraryDescription,
              cpp2);
      enhancer.createBuildRule(
          TargetGraph.EMPTY,
          flavoredParams,
          resolver,
          arg,
          ImmutableMap.of(),
          ImmutableSortedSet.of());
    }
  }

}
