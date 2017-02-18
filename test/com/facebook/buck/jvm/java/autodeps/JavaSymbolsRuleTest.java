/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.jvm.java.autodeps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.JavaFileParser;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.SourcePaths;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.MoreCollectors;
import com.facebook.buck.util.ObjectMappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class JavaSymbolsRuleTest {
  @Rule
  public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  public void ensureJsonFilesGetWritten() throws IOException, InterruptedException {
    TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "java_library_symbols_finder",
        tmp)
        .setUp();
    ProjectFilesystem projectFilesystem = new ProjectFilesystem(tmp.getRoot());

    ImmutableSortedSet<SourcePath> srcs = ImmutableSortedSet.<SourcePath>naturalOrder()
        .addAll(
            Stream.of("Example1.java", "Example2.java")
                .map(Paths::get)
                .map(SourcePaths.toSourcePath(projectFilesystem)::apply)
                .iterator())
        .add(new BuildTargetSourcePath(BuildTargetFactory.newInstance("//foo:bar")))
        .build();
    JavaFileParser javaFileParser = JavaFileParser.createJavaFileParser(
        JavacOptions.builder()
            .setSourceLevel("7")
            .setTargetLevel("7")
            .build());
    JavaLibrarySymbolsFinder symbolsFinder = new JavaLibrarySymbolsFinder(
        srcs,
        javaFileParser,
        /* shouldRecordRequiredSymbols */ true);

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//:examples");
    JavaSymbolsRule javaSymbolsRule = new JavaSymbolsRule(
        buildTarget,
        symbolsFinder,
        /* generatedSymbols */ ImmutableSortedSet.of("com.example.generated.Example"),
        ObjectMappers.newDefaultInstance(),
        projectFilesystem
    );
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    resolver.addToIndex(javaSymbolsRule);
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    List<Step> buildSteps = javaSymbolsRule.getBuildSteps(
        FakeBuildContext.withSourcePathResolver(pathResolver),
        /* buildableContext */ null);

    ExecutionContext executionContext = TestExecutionContext.newInstance();
    ObjectMapper objectMapper = ObjectMappers.newDefaultInstance();

    for (Step step : buildSteps) {
      step.execute(executionContext);
    }

    JsonNode jsonNode = objectMapper.readTree(
        projectFilesystem
            .resolve(
                BuildTargets.getGenPath(
                    javaSymbolsRule.getProjectFilesystem(),
                    buildTarget.withFlavors(JavaSymbolsRule.JAVA_SYMBOLS),
                    "__%s__.json"))
            .toFile());
    assertTrue(jsonNode instanceof ObjectNode);
    assertEquals(
        ImmutableSet.of(
            "com.example.Example1",
            "com.example.Example2",
            "com.example.generated.Example"),
        StreamSupport.stream(jsonNode.get("provided").spliterator(), false)
            .map(JsonNode::textValue)
            .collect(MoreCollectors.toImmutableSet()));
    assertEquals(
        ImmutableSet.of(
            "com.example.other.Bar",
            "com.example.other.Foo"),
        StreamSupport.stream(jsonNode.get("required").spliterator(), false)
            .map(JsonNode::textValue)
            .collect(MoreCollectors.toImmutableSet()));
  }
}
