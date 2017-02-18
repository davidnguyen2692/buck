/*
 * Copyright 2012-present Facebook, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.jvm.java.Keystore;
import com.facebook.buck.jvm.java.KeystoreBuilder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.parser.BuildTargetParser;
import com.facebook.buck.parser.BuildTargetPatternParser;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.shell.AbstractGenruleStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.MkdirAndSymlinkFileStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.MoreAsserts;
import com.facebook.buck.util.MoreCollectors;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import org.easymock.EasyMock;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class ApkGenruleTest {

  private void createSampleAndroidBinaryRule(
      BuildRuleResolver ruleResolver,
      ProjectFilesystem filesystem) throws NoSuchBuildTargetException {
    // Create a java_binary that depends on a java_library so it is possible to create a
    // java_binary rule with a classpath entry and a main class.
    BuildTarget libAndroidTarget =
        BuildTargetFactory.newInstance(filesystem.getRootPath(), "//:lib-android");
    BuildRule androidLibRule = JavaLibraryBuilder.createBuilder(libAndroidTarget)
        .addSrc(Paths.get("java/com/facebook/util/Facebook.java"))
        .build(ruleResolver, filesystem);

    BuildTarget keystoreTarget =
        BuildTargetFactory.newInstance(filesystem.getRootPath(), "//keystore:debug");
    Keystore keystore = KeystoreBuilder.createBuilder(keystoreTarget)
        .setStore(new FakeSourcePath(filesystem, "keystore/debug.keystore"))
        .setProperties(new FakeSourcePath(filesystem, "keystore/debug.keystore.properties"))
        .build(ruleResolver, filesystem);

    AndroidBinaryBuilder
        .createBuilder(BuildTargetFactory.newInstance(filesystem.getRootPath(), "//:fb4a"))
        .setManifest(new FakeSourcePath("AndroidManifest.xml"))
        .setOriginalDeps(ImmutableSortedSet.of(androidLibRule.getBuildTarget()))
        .setKeystore(keystore.getBuildTarget())
        .build(ruleResolver, filesystem);
  }

  @Test
  @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
  public void testCreateAndRunApkGenrule() throws IOException, NoSuchBuildTargetException {
    ProjectFilesystem projectFilesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();
    FileSystem fileSystem = projectFilesystem.getRootPath().getFileSystem();
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    createSampleAndroidBinaryRule(ruleResolver, projectFilesystem);

    // From the Python object, create a ApkGenruleBuildRuleFactory to create a ApkGenrule.Builder
    // that builds a ApkGenrule from the Python object.
    BuildTargetParser parser = EasyMock.createNiceMock(BuildTargetParser.class);
    final BuildTarget apkTarget =
        BuildTargetFactory.newInstance(projectFilesystem.getRootPath(), "//:fb4a");

    EasyMock.expect(
        parser.parse(
            EasyMock.eq(":fb4a"),
            EasyMock.anyObject(BuildTargetPatternParser.class),
            EasyMock.anyObject()))
        .andStubReturn(apkTarget);
    EasyMock.replay(parser);
    BuildTarget buildTarget =
        BuildTargetFactory.newInstance(
            projectFilesystem.getRootPath(),
            "//src/com/facebook:sign_fb4a");
    ApkGenruleDescription description = new ApkGenruleDescription();
    ApkGenruleDescription.Arg arg = description.createUnpopulatedConstructorArg();
    SourcePathResolver pathResolver =
        new SourcePathResolver(new SourcePathRuleFinder(ruleResolver));
    arg.apk = new FakeInstallable(apkTarget, pathResolver).getBuildTarget();
    arg.bash = Optional.of("");
    arg.cmd = Optional.of("python signer.py $APK key.properties > $OUT");
    arg.cmdExe = Optional.of("");
    arg.type = Optional.empty();
    arg.out = "signed_fb4a.apk";
    arg.srcs = ImmutableList.of(
        new PathSourcePath(projectFilesystem, fileSystem.getPath("src/com/facebook/signer.py")),
        new PathSourcePath(
            projectFilesystem,
            fileSystem.getPath("src/com/facebook/key.properties")));
    arg.tests = ImmutableSortedSet.of();
    BuildRuleParams params = new FakeBuildRuleParamsBuilder(buildTarget)
        .setProjectFilesystem(projectFilesystem).build();
    ApkGenrule apkGenrule =
        (ApkGenrule) description.createBuildRule(TargetGraph.EMPTY, params, ruleResolver, arg);
    ruleResolver.addToIndex(apkGenrule);

    // Verify all of the observers of the Genrule.
    String expectedApkOutput =
        projectFilesystem.resolve(
            projectFilesystem.getBuckPaths().getGenDir().toString() +
             "/src/com/facebook/sign_fb4a/sign_fb4a.apk").toString();
    assertEquals(expectedApkOutput,
        apkGenrule.getAbsoluteOutputFilePath(pathResolver));
    assertEquals(
        "The apk that this rule is modifying must have the apk in its deps.",
        ImmutableSet.of(apkTarget.toString()),
        apkGenrule.getDeps().stream()
            .map(Object::toString)
            .collect(MoreCollectors.toImmutableSet()));
    BuildContext buildContext = FakeBuildContext.withSourcePathResolver(pathResolver);
    Iterable<Path> expectedInputsToCompareToOutputs = ImmutableList.of(
        fileSystem.getPath("src/com/facebook/signer.py"),
        fileSystem.getPath("src/com/facebook/key.properties"));
    MoreAsserts.assertIterablesEquals(
        expectedInputsToCompareToOutputs,
        pathResolver.filterInputsToCompareToOutput(apkGenrule.getSrcs()));

    // Verify that the shell commands that the genrule produces are correct.
    List<Step> steps = apkGenrule.getBuildSteps(buildContext, new FakeBuildableContext());
    assertEquals(7, steps.size());

    Step firstStep = steps.get(0);
    assertTrue(firstStep instanceof RmStep);
    RmStep rmCommand = (RmStep) firstStep;
    ExecutionContext executionContext = newEmptyExecutionContext();
    assertEquals(
        "First command should delete the output file to be written by the genrule.",
        ImmutableList.of(
            "rm",
            "-f",
            "-r",
            expectedApkOutput),
        rmCommand.getShellCommand());

    Step secondStep = steps.get(1);
    assertTrue(secondStep instanceof MkdirStep);
    MkdirStep mkdirCommand = (MkdirStep) secondStep;
    Path mkdirDir =
        projectFilesystem.resolve(
            projectFilesystem.getBuckPaths().getGenDir().resolve("src/com/facebook/sign_fb4a"));
    assertEquals(
        "Second command should make sure the output directory exists.",
        mkdirDir,
        mkdirCommand.getPath());

    Step thirdStep = steps.get(2);
    assertTrue(thirdStep instanceof MakeCleanDirectoryStep);
    MakeCleanDirectoryStep secondMkdirCommand = (MakeCleanDirectoryStep) thirdStep;
    Path relativePathToTmpDir = projectFilesystem.getBuckPaths().getGenDir().resolve(
        "src/com/facebook/sign_fb4a__tmp");
    assertEquals(
        "Third command should make sure the temp directory exists.",
        relativePathToTmpDir,
        secondMkdirCommand.getPath());

    Step fourthStep = steps.get(3);
    assertTrue(fourthStep instanceof MakeCleanDirectoryStep);
    MakeCleanDirectoryStep thirdMkdirCommand = (MakeCleanDirectoryStep) fourthStep;
    Path relativePathToSrcDir = projectFilesystem.getBuckPaths().getGenDir().resolve(
        "src/com/facebook/sign_fb4a__srcs");
    assertEquals(
        "Fourth command should make sure the temp directory exists.",
        relativePathToSrcDir,
        thirdMkdirCommand.getPath());

    MkdirAndSymlinkFileStep linkSource1 = (MkdirAndSymlinkFileStep) steps.get(4);
    assertEquals(fileSystem.getPath("src/com/facebook/signer.py"), linkSource1.getSource());
    assertEquals(fileSystem.getPath(relativePathToSrcDir + "/signer.py"), linkSource1.getTarget());

    MkdirAndSymlinkFileStep linkSource2 = (MkdirAndSymlinkFileStep) steps.get(5);
    assertEquals(fileSystem.getPath("src/com/facebook/key.properties"), linkSource2.getSource());
    assertEquals(
        fileSystem.getPath(relativePathToSrcDir + "/key.properties"),
        linkSource2.getTarget());

    Step seventhStep = steps.get(6);
    assertTrue(seventhStep instanceof AbstractGenruleStep);
    AbstractGenruleStep genruleCommand = (AbstractGenruleStep) seventhStep;
    assertEquals("genrule", genruleCommand.getShortName());
    ImmutableMap<String, String> environmentVariables = genruleCommand.getEnvironmentVariables(
        executionContext);
    assertEquals(new ImmutableMap.Builder<String, String>()
        .put(
            "APK", projectFilesystem.resolve(
                BuildTargets.getGenPath(projectFilesystem, apkTarget, "%s.apk")).toString())
        .put("OUT", expectedApkOutput).build(),
        environmentVariables);

    Path scriptFilePath = genruleCommand.getScriptFilePath(executionContext);
    String scriptFileContents = genruleCommand.getScriptFileContents(executionContext);
    assertEquals(
        ImmutableList.of("/bin/bash", "-e", scriptFilePath.toString()),
        genruleCommand.getShellCommand(executionContext));
    assertEquals("python signer.py $APK key.properties > $OUT", scriptFileContents);

    EasyMock.verify(parser);
  }

  private ExecutionContext newEmptyExecutionContext() {
    return TestExecutionContext.newBuilder()
        .setPlatform(Platform.LINUX) // Fix platform to Linux to use bash in genrule.
        .build();
  }

  private static class FakeInstallable extends FakeBuildRule implements HasInstallableApk {

    public FakeInstallable(BuildTarget buildTarget, SourcePathResolver resolver) {
      super(buildTarget, resolver);
    }

    @Override
    public ApkInfo getApkInfo() {
      return ApkInfo.builder()
          .setApkPath(new FakeSourcePath("buck-out/gen/fb4a.apk"))
          .setManifestPath(new FakeSourcePath("spoof"))
          .build();
    }
  }
}
