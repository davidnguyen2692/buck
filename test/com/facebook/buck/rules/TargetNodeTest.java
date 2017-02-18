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

package com.facebook.buck.rules;

import static com.facebook.buck.rules.TestCellBuilder.createCellRoots;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.graph.MutableDirectedGraph;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.coercer.DefaultTypeCoercerFactory;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.util.MoreCollectors;
import com.facebook.buck.util.ObjectMappers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.Hashing;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

public class TargetNodeTest {

  public static final BuildTarget TARGET_THREE =
      BuildTargetFactory.newInstance("//example/path:three");

  private static final TargetGraph GRAPH = new TargetGraph(
      new MutableDirectedGraph<TargetNode<?, ?>>(),
      ImmutableMap.of(),
      ImmutableSet.of());

  @Test
  public void testIgnoreNonBuildTargetOrPathOrSourcePathArgument()
      throws NoSuchBuildTargetException {

    TargetNode<Arg, ExampleDescription> targetNode = createTargetNode(TARGET_THREE);

    assertTrue(targetNode.getExtraDeps().isEmpty());
    assertTrue(targetNode.getDeclaredDeps().isEmpty());
  }

  @Test
  public void testDepsAndPathsAreCollected() throws NoSuchBuildTargetException {
    ImmutableList<String> depsStrings = ImmutableList.of(
        "//example/path:one",
        "//example/path:two");
    ImmutableSet<BuildTarget> depsTargets = depsStrings.stream()
        .map(BuildTargetFactory::newInstance)
        .collect(MoreCollectors.toImmutableSet());
    ImmutableMap<String, Object> rawNode = ImmutableMap.of(
        "deps", depsStrings,
        "sourcePaths", ImmutableList.of("//example/path:four", "MyClass.java"),
        "appleSource", "//example/path:five",
        "source", "AnotherClass.java");

    TargetNode<Arg, ExampleDescription> targetNode =
        createTargetNode(TARGET_THREE, depsTargets, rawNode);

    assertThat(
        targetNode.getInputs(),
        containsInAnyOrder(
            Paths.get("example/path/MyClass.java"),
            Paths.get("example/path/AnotherClass.java")));

    assertThat(
        targetNode.getExtraDeps(),
        containsInAnyOrder(
            BuildTargetFactory.newInstance("//example/path:four"),
            BuildTargetFactory.newInstance("//example/path:five")));

    assertThat(
        targetNode.getDeclaredDeps(),
        containsInAnyOrder(
            BuildTargetFactory.newInstance("//example/path:one"),
            BuildTargetFactory.newInstance("//example/path:two")));
  }

  @Test
  public void targetsWithTheSameRelativePathButNotTheSameCellMightNotBeAbleToSeeEachOther()
      throws Exception {

    ProjectFilesystem rootOne = FakeProjectFilesystem.createJavaOnlyFilesystem("/one");
    BuildTarget buildTargetOne = BuildTargetFactory.newInstance(rootOne, "//foo:bar");
    TargetNode<Arg, ExampleDescription> targetNodeOne = createTargetNode(buildTargetOne);

    ProjectFilesystem rootTwo = FakeProjectFilesystem.createJavaOnlyFilesystem("/two");
    BuildTarget buildTargetTwo = BuildTargetFactory.newInstance(rootTwo, "//foo:bar");
    TargetNode<Arg, ExampleDescription> targetNodeTwo = createTargetNode(buildTargetTwo);

    boolean isVisible = targetNodeOne.isVisibleTo(GRAPH, targetNodeTwo);

    assertThat(isVisible, is(false));
  }

  public static class Arg extends AbstractDescriptionArg {
    public ImmutableSortedSet<BuildTarget> deps;
    public ImmutableSortedSet<SourcePath> sourcePaths;
    public Optional<SourceWithFlags> appleSource;
    public Optional<Path> source;
    public Optional<String> string;
    @Hint(isDep = false)
    public Optional<BuildTarget> target;
  }

  public static class ExampleDescription implements Description<Arg> {

    @Override
    public Arg createUnpopulatedConstructorArg() {
      return new Arg();
    }

    @Override
    public <A extends Arg> BuildRule createBuildRule(
        TargetGraph targetGraph,
        BuildRuleParams params,
        BuildRuleResolver resolver,
        A args) {
      return new FakeBuildRule(params, new SourcePathResolver(new SourcePathRuleFinder(resolver)));
    }
  }

  private static TargetNode<Arg, ExampleDescription> createTargetNode(
      BuildTarget buildTarget)
      throws NoSuchBuildTargetException {
    ImmutableMap<String, Object> rawNode = ImmutableMap.of(
        "deps", ImmutableList.of(),
        "string", "//example/path:one",
        "target", "//example/path:two",
        "sourcePaths", ImmutableSortedSet.of());

    return createTargetNode(buildTarget, ImmutableSet.of(), rawNode);
  }

  private static TargetNode<Arg, ExampleDescription> createTargetNode(
      BuildTarget buildTarget,
      ImmutableSet<BuildTarget> declaredDeps,
      ImmutableMap<String, Object> rawNode) throws NoSuchBuildTargetException {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();

    ExampleDescription description = new ExampleDescription();

    return new TargetNodeFactory(new DefaultTypeCoercerFactory(ObjectMappers.newDefaultInstance()))
        .create(
            Hashing.sha1().hashString(buildTarget.getFullyQualifiedName(), UTF_8),
            description,
            createPopulatedConstructorArg(
                description,
                buildTarget,
                rawNode),
            filesystem,
            buildTarget,
            declaredDeps,
            ImmutableSet.of(),
            createCellRoots(filesystem));
  }


  private static Arg createPopulatedConstructorArg(
      Description<Arg> description,
      BuildTarget buildTarget,
      Map<String, Object> instance) throws NoSuchBuildTargetException {
    ConstructorArgMarshaller marshaller =
        new ConstructorArgMarshaller(new DefaultTypeCoercerFactory(
            ObjectMappers.newDefaultInstance()));
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    Arg constructorArg = description.createUnpopulatedConstructorArg();
    try {
      marshaller.populate(
          createCellRoots(projectFilesystem),
          projectFilesystem,
          buildTarget,
          constructorArg,
          ImmutableSet.builder(),
          ImmutableSet.builder(),
          instance);
    } catch (ParamInfoException e) {
      throw new RuntimeException(e);
    }
    return constructorArg;
  }
}
