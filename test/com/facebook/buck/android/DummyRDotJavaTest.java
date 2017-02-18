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

import static com.facebook.buck.jvm.java.JavaCompilationConstants.ANDROID_JAVAC_OPTIONS;
import static org.junit.Assert.assertEquals;

import com.facebook.buck.io.MorePaths;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.MoreAsserts;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class DummyRDotJavaTest {
  @Test
  public void testBuildSteps() throws IOException {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(ruleResolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);
    BuildRule resourceRule1 = ruleResolver.addToIndex(
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(ruleFinder)
            .setBuildTarget(BuildTargetFactory.newInstance("//android_res/com/example:res1"))
            .setRDotJavaPackage("com.facebook")
            .setRes(new FakeSourcePath("android_res/com/example/res1"))
            .build());
    setAndroidResourceBuildOutput(resourceRule1);
    BuildRule resourceRule2 = ruleResolver.addToIndex(
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(ruleFinder)
            .setBuildTarget(BuildTargetFactory.newInstance("//android_res/com/example:res2"))
            .setRDotJavaPackage("com.facebook")
            .setRes(new FakeSourcePath("android_res/com/example/res2"))
            .build());
    setAndroidResourceBuildOutput(resourceRule2);

    DummyRDotJava dummyRDotJava = new DummyRDotJava(
        new FakeBuildRuleParamsBuilder(BuildTargetFactory.newInstance("//java/base:rule"))
            .setProjectFilesystem(filesystem)
            .build(),
        ruleFinder,
        ImmutableSet.of(
            (HasAndroidResourceDeps) resourceRule1,
            (HasAndroidResourceDeps) resourceRule2),
        ANDROID_JAVAC_OPTIONS,
        /* forceFinalResourceIds */ false,
        Optional.empty(),
        Optional.of("R2"));

    FakeBuildableContext buildableContext = new FakeBuildableContext();
    List<Step> steps = dummyRDotJava.getBuildSteps(
        FakeBuildContext.NOOP_CONTEXT,
        buildableContext);
    assertEquals("DummyRDotJava returns an incorrect number of Steps.", 10, steps.size());

    String rDotJavaSrcFolder =
        BuildTargets
            .getScratchPath(filesystem, dummyRDotJava.getBuildTarget(), "__%s_rdotjava_src__")
            .toString();
    String rDotJavaBinFolder =
        BuildTargets
            .getScratchPath(filesystem, dummyRDotJava.getBuildTarget(), "__%s_rdotjava_bin__")
            .toString();
    String rDotJavaAbiFolder =
        BuildTargets
            .getGenPath(filesystem, dummyRDotJava.getBuildTarget(), "__%s_dummyrdotjava_abi__")
            .toString();
    String rDotJavaOutputFolder =
        BuildTargets
            .getGenPath(filesystem, dummyRDotJava.getBuildTarget(), "__%s_dummyrdotjava_output__")
            .toString();
    String rDotJavaOutputJar =
        MorePaths.pathWithPlatformSeparators(String.format(
            "%s/%s.jar",
            rDotJavaOutputFolder,
            dummyRDotJava.getBuildTarget().getShortNameAndFlavorPostfix()));
    String genFolder = Paths.get("buck-out/gen/java/base/").toString();

    List<String> sortedSymbolsFiles =
        Stream.of((AndroidResource) resourceRule1, (AndroidResource) resourceRule2)
            .map(Object::toString)
            .collect(MoreCollectors.toImmutableList());
    ImmutableSortedSet<Path> javaSourceFiles = ImmutableSortedSet.of(
        Paths.get(rDotJavaSrcFolder).resolve("com/facebook/R.java"));
    List<String> expectedStepDescriptions = Lists.newArrayList(
        makeCleanDirDescription(filesystem.resolve(rDotJavaSrcFolder)),
        "android-res-merge " + Joiner.on(' ').join(sortedSymbolsFiles),
        "android-res-merge " + Joiner.on(' ').join(sortedSymbolsFiles),
        makeCleanDirDescription(filesystem.resolve(rDotJavaBinFolder)),
        makeCleanDirDescription(filesystem.resolve(rDotJavaAbiFolder)),
        makeCleanDirDescription(filesystem.resolve(rDotJavaOutputFolder)),
        String.format("mkdir -p %s", filesystem.resolve(genFolder)),
        RDotJava.createJavacStepForDummyRDotJavaFiles(
            javaSourceFiles,
            BuildTargets.getGenPath(filesystem, dummyRDotJava.getBuildTarget(), "__%s__srcs"),
            Paths.get(rDotJavaBinFolder),
            ANDROID_JAVAC_OPTIONS,
        /* buildTarget */ null,
            pathResolver,
            ruleFinder,
            new FakeProjectFilesystem())
            .getDescription(TestExecutionContext.newInstance()),
        String.format("jar cf %s  %s", rDotJavaOutputJar, rDotJavaBinFolder),
        String.format("calculate_abi %s", rDotJavaBinFolder));

    MoreAsserts.assertSteps(
        "DummyRDotJava.getBuildSteps() must return these exact steps.",
        expectedStepDescriptions,
        steps,
        TestExecutionContext.newInstance());

    assertEquals(ImmutableSet.of(Paths.get(rDotJavaBinFolder), Paths.get(rDotJavaOutputJar)),
        buildableContext.getRecordedArtifacts());
  }

  @Test
  public void testRDotJavaBinFolder() {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(
        new BuildRuleResolver(
            TargetGraph.EMPTY,
            new DefaultTargetNodeToBuildRuleTransformer()));
    DummyRDotJava dummyRDotJava = new DummyRDotJava(
        new FakeBuildRuleParamsBuilder(BuildTargetFactory.newInstance("//java/com/example:library"))
            .build(),
        ruleFinder,
        ImmutableSet.of(),
        ANDROID_JAVAC_OPTIONS,
        /* forceFinalResourceIds */ false,
        Optional.empty(),
        Optional.empty());
    assertEquals(
        BuildTargets.getScratchPath(
            dummyRDotJava.getProjectFilesystem(),
            dummyRDotJava.getBuildTarget(),
            "__%s_rdotjava_bin__"),
        dummyRDotJava.getRDotJavaBinFolder());
  }

  private static String makeCleanDirDescription(Path dirname) {
    return String.format("rm -f -r %s && mkdir -p %s", dirname, dirname);
  }

  private void setAndroidResourceBuildOutput(BuildRule resourceRule) {
    if (resourceRule instanceof AndroidResource) {
      ((AndroidResource) resourceRule)
          .getBuildOutputInitializer();
    }
  }
}
