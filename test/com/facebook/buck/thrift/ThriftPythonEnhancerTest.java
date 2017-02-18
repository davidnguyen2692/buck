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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.python.PythonLibrary;
import com.facebook.buck.python.PythonTestUtils;
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
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.args.StringArg;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class ThriftPythonEnhancerTest {

  private static final BuildTarget TARGET = BuildTargetFactory.newInstance("//:test#python");
  private static final BuckConfig BUCK_CONFIG = FakeBuckConfig.builder().build();
  private static final ThriftBuckConfig THRIFT_BUCK_CONFIG = new ThriftBuckConfig(BUCK_CONFIG);
  private static final ThriftPythonEnhancer ENHANCER =
      new ThriftPythonEnhancer(
          THRIFT_BUCK_CONFIG,
          ThriftPythonEnhancer.Type.NORMAL);
  private static final ThriftPythonEnhancer TWISTED_ENHANCER =
      new ThriftPythonEnhancer(
          THRIFT_BUCK_CONFIG,
          ThriftPythonEnhancer.Type.TWISTED);
  private static final ThriftPythonEnhancer ASYNCIO_ENHANCER =
      new ThriftPythonEnhancer(
          THRIFT_BUCK_CONFIG,
          ThriftPythonEnhancer.Type.ASYNCIO);

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
        "py",
        ENHANCER.getLanguage());
  }

  @Test
  public void getFlavor() {
    assertEquals(
        ImmutableFlavor.of("py"),
        ENHANCER.getFlavor());
  }

  private ImmutableSet<String> addTwisted(ImmutableSet<String> options) {
    return ImmutableSet.<String>builder()
        .add("twisted")
        .addAll(options)
        .build();
  }

  private ImmutableSet<String> addAsyncio(ImmutableSet<String> options) {
    return ImmutableSet.<String>builder()
        .add("asyncio")
        .addAll(options)
        .build();
  }

  @Test
  public void getOptions() {
    ThriftConstructorArg arg = new ThriftConstructorArg();
    ImmutableSet<String> options;

    // Test empty options.
    options = ImmutableSet.of();
    arg.pyOptions = options;
    assertEquals(
        options,
        ENHANCER.getOptions(TARGET, arg));
    assertEquals(
        addTwisted(options),
        TWISTED_ENHANCER.getOptions(TARGET, arg));

    // Test set options.
    options = ImmutableSet.of("test", "option");
    arg.pyOptions = options;
    assertEquals(
        options,
        ENHANCER.getOptions(TARGET, arg));
    assertEquals(
        addTwisted(options),
        TWISTED_ENHANCER.getOptions(TARGET, arg));
    assertEquals(
        addAsyncio(options),
        ASYNCIO_ENHANCER.getOptions(TARGET, arg));

    // Test absent options.
    arg.pyOptions = ImmutableSet.of();
    assertEquals(
        ImmutableSet.<String>of(),
        ENHANCER.getOptions(TARGET, arg));
    assertEquals(
        addTwisted(ImmutableSet.of()),
        TWISTED_ENHANCER.getOptions(TARGET, arg));
    assertEquals(
        addAsyncio(ImmutableSet.of()),
        ASYNCIO_ENHANCER.getOptions(TARGET, arg));
  }

  private void expectImplicitDeps(
      ThriftPythonEnhancer enhancer,
      ImmutableSet<String> options,
      ImmutableSet<BuildTarget> expected) {

    ThriftConstructorArg arg = new ThriftConstructorArg();
    arg.pyOptions = options;

    assertEquals(
        expected,
        enhancer.getImplicitDepsForTargetFromConstructorArg(TARGET, arg));
  }

  @Test
  public void getImplicitDeps() {
    // Setup enhancers which set all appropriate values in the config.
    ImmutableMap<String, BuildTarget> config = ImmutableMap.of(
        "python_library", BuildTargetFactory.newInstance("//:python_library"),
        "python_twisted_library", BuildTargetFactory.newInstance("//:python_twisted_library"),
        "python_asyncio_library", BuildTargetFactory.newInstance("//:python_asyncio_library"));
    ImmutableMap.Builder<String, String> strConfig = ImmutableMap.builder();
    for (ImmutableMap.Entry<String, BuildTarget> ent : config.entrySet()) {
      strConfig.put(ent.getKey(), ent.getValue().toString());
    }
    BuckConfig buckConfig = FakeBuckConfig.builder().setSections(
        ImmutableMap.of("thrift", strConfig.build())).build();
    ThriftBuckConfig thriftBuckConfig = new ThriftBuckConfig(buckConfig);
    ThriftPythonEnhancer enhancer =
        new ThriftPythonEnhancer(
            thriftBuckConfig,
            ThriftPythonEnhancer.Type.NORMAL);
    ThriftPythonEnhancer twistedEnhancer =
        new ThriftPythonEnhancer(
            thriftBuckConfig,
            ThriftPythonEnhancer.Type.TWISTED);
    ThriftPythonEnhancer asyncioEnhancer =
        new ThriftPythonEnhancer(
            thriftBuckConfig,
            ThriftPythonEnhancer.Type.ASYNCIO);

    // With no options we just need to find the python thrift library.
    expectImplicitDeps(
        enhancer,
        ImmutableSet.of(),
        ImmutableSet.of(
            config.get("python_library")));

    // With the twisted enhancer option we also expect the twisted library.
    expectImplicitDeps(
        twistedEnhancer,
        ImmutableSet.of("twisted"),
        ImmutableSet.of(
            config.get("python_library"),
            config.get("python_twisted_library")));

    // With the asyncio enhancer option we also expect the asyncio library.
    expectImplicitDeps(
        asyncioEnhancer,
        ImmutableSet.of("asyncio"),
        ImmutableSet.of(
            config.get("python_library"),
            config.get("python_asyncio_library")));
  }

  @Test
  public void createBuildRule() {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    BuildRuleParams flavoredParams = new FakeBuildRuleParamsBuilder(TARGET).build();

    // Add a dummy dependency to the constructor arg to make sure it gets through.
    ThriftConstructorArg arg = new ThriftConstructorArg();
    arg.pyOptions = ImmutableSet.of();
    arg.pyBaseModule = Optional.empty();

    // Setup up some thrift inputs to pass to the createBuildRule method.
    ImmutableMap<String, ThriftSource> sources = ImmutableMap.of(
        "test1.thrift", new ThriftSource(
            createFakeThriftCompiler("//:thrift_source1"),
            ImmutableList.of(),
            Paths.get("output1")),
        "test2.thrift", new ThriftSource(
            createFakeThriftCompiler("//:thrift_source2"),
            ImmutableList.of(),
            Paths.get("output2")));

    // Create a dummy implicit dep to pass in.
    ImmutableSortedSet<BuildRule> deps = ImmutableSortedSet.of(
        createFakeBuildRule("//:dep", pathResolver));

    // Run the enhancer to create the language specific build rule.
    BuildRule library = ENHANCER
        .createBuildRule(TargetGraph.EMPTY, flavoredParams, resolver, arg, sources, deps);

    // Verify that the top-level default python lib has correct deps.
    assertEquals(deps, library.getDeps());
  }

  @Test
  public void baseModule() {
    BuildTarget target = BuildTargetFactory.newInstance("//test:test");
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    BuildRuleParams flavoredParams =
        new FakeBuildRuleParamsBuilder(target).build();

    // Add a dummy dependency to the constructor arg to make sure it gets through.
    ThriftConstructorArg arg = new ThriftConstructorArg();
    arg.pyOptions = ImmutableSet.of();

    // Setup up some thrift inputs to pass to the createBuildRule method.
    ImmutableMap<String, ThriftSource> sources = ImmutableMap.of(
        "test.thrift", new ThriftSource(
            createFakeThriftCompiler("//:thrift_source"),
            ImmutableList.of(),
            Paths.get("output")));

    // Verify that not setting the base module parameter defaults to the build target base path.
    arg.pyBaseModule = Optional.empty();
    PythonLibrary normal = ENHANCER
        .createBuildRule(
            TargetGraph.EMPTY,
            flavoredParams,
            resolver,
            arg,
            sources,
            ImmutableSortedSet.of());
    for (ImmutableMap.Entry<Path, SourcePath> ent :
         normal.getSrcs(PythonTestUtils.PYTHON_PLATFORM).entrySet()) {
      assertTrue(ent.getKey().toString(), ent.getKey().startsWith(target.getBasePath()));
    }

    // Verify that setting the base module uses it correctly.
    arg.pyBaseModule = Optional.of("blah");
    PythonLibrary baseModule = ENHANCER
        .createBuildRule(
            TargetGraph.EMPTY,
            flavoredParams,
            resolver,
            arg,
            sources,
            ImmutableSortedSet.of());
    for (ImmutableMap.Entry<Path, SourcePath> ent :
         baseModule.getSrcs(PythonTestUtils.PYTHON_PLATFORM).entrySet()) {
      assertTrue(ent.getKey().startsWith(Paths.get(arg.pyBaseModule.get())));
    }
  }

  @Test
  public void twistedBaseModule() {
    BuildTarget target = BuildTargetFactory.newInstance("//test:test");
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    BuildRuleParams flavoredParams =
        new FakeBuildRuleParamsBuilder(target).build();

    // Add a dummy dependency to the constructor arg to make sure it gets through.
    ThriftConstructorArg arg = new ThriftConstructorArg();
    arg.pyOptions = ImmutableSet.of();

    // Setup up some thrift inputs to pass to the createBuildRule method.
    ImmutableMap<String, ThriftSource> sources = ImmutableMap.of(
        "test.thrift", new ThriftSource(
            createFakeThriftCompiler("//:thrift_source"),
            ImmutableList.of(),
            Paths.get("output")));

    // Verify that not setting the base module parameter defaults to the build target base path.
    arg.pyTwistedBaseModule = Optional.empty();
    PythonLibrary normal = TWISTED_ENHANCER
        .createBuildRule(
            TargetGraph.EMPTY,
            flavoredParams,
            resolver,
            arg,
            sources,
            ImmutableSortedSet.of());
    for (ImmutableMap.Entry<Path, SourcePath> ent :
         normal.getSrcs(PythonTestUtils.PYTHON_PLATFORM).entrySet()) {
      assertTrue(ent.getKey().toString(), ent.getKey().startsWith(target.getBasePath()));
    }

    // Verify that setting the base module uses it correctly.
    arg.pyTwistedBaseModule = Optional.of("blah");
    PythonLibrary baseModule = TWISTED_ENHANCER
        .createBuildRule(
            TargetGraph.EMPTY,
            flavoredParams,
            resolver,
            arg,
            sources,
            ImmutableSortedSet.of());
    for (ImmutableMap.Entry<Path, SourcePath> ent :
         baseModule.getSrcs(PythonTestUtils.PYTHON_PLATFORM).entrySet()) {
      assertTrue(ent.getKey().startsWith(Paths.get(arg.pyTwistedBaseModule.get())));
    }
  }

  @Test
  public void asyncioBaseModule() {
    BuildTarget target = BuildTargetFactory.newInstance("//test:test");
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    BuildRuleParams flavoredParams =
        new FakeBuildRuleParamsBuilder(target).build();

    // Add a dummy dependency to the constructor arg to make sure it gets through.
    ThriftConstructorArg arg = new ThriftConstructorArg();
    arg.pyOptions = ImmutableSet.of();

    // Setup up some thrift inputs to pass to the createBuildRule method.
    ImmutableMap<String, ThriftSource> sources = ImmutableMap.of(
        "test.thrift", new ThriftSource(
            createFakeThriftCompiler("//:thrift_source"),
            ImmutableList.of(),
            Paths.get("output")));

    // Verify that not setting the base module parameter defaults to the build target base path.
    arg.pyAsyncioBaseModule = Optional.empty();
    PythonLibrary normal =
        ASYNCIO_ENHANCER.createBuildRule(
            TargetGraph.EMPTY,
            flavoredParams,
            resolver,
            arg,
            sources,
            ImmutableSortedSet.of());
    for (ImmutableMap.Entry<Path, SourcePath> ent :
        normal.getSrcs(PythonTestUtils.PYTHON_PLATFORM).entrySet()) {
      assertTrue(ent.getKey().toString(), ent.getKey().startsWith(target.getBasePath()));
    }

    // Verify that setting the base module uses it correctly.
    arg.pyAsyncioBaseModule = Optional.of("blah");
    PythonLibrary baseModule =
        ASYNCIO_ENHANCER.createBuildRule(
            TargetGraph.EMPTY,
            flavoredParams,
            resolver,
            arg,
            sources,
            ImmutableSortedSet.of());
    for (ImmutableMap.Entry<Path, SourcePath> ent :
        baseModule.getSrcs(PythonTestUtils.PYTHON_PLATFORM).entrySet()) {
      assertTrue(ent.getKey().startsWith(Paths.get(arg.pyAsyncioBaseModule.get())));
    }
  }

}
