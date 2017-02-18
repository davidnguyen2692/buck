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

package com.facebook.buck.android;

import com.facebook.buck.android.aapt.RDotTxtEntry.RType;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.FakeJavaLibrary;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.FakeOnDiskBuildInfo;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;

public class TrimUberRDotJavaTest {
  @Rule
  public TemporaryPaths tmpFolder = new TemporaryPaths();

  @Test
  public void testTrimming() throws IOException, InterruptedException {
    Optional<String> keepResourcePattern = Optional.empty();
    String rDotJavaContentsAfterFiltering =
        "package com.test;\n" +
            "\n" +
            "public class R {\n" +
            "  public static class string {\n" +
            "    public static final int my_first_resource=0x7f08005c;\n" +
            "  }\n" +
            "}\n";
    doTrimingTest(keepResourcePattern, rDotJavaContentsAfterFiltering);
  }

  @Test
  public void testTrimmingWithKeepPattern() throws IOException, InterruptedException {
    Optional<String> keepResourcePattern = Optional.of("^keep_resource.*");
    String rDotJavaContentsAfterFiltering =
        "package com.test;\n" +
            "\n" +
            "public class R {\n" +
            "  public static class string {\n" +
            "    public static final int my_first_resource=0x7f08005c;\n" +
            "    public static final int keep_resource=0x7f083bc2;\n" +
            "  }\n" +
            "}\n";
    doTrimingTest(keepResourcePattern, rDotJavaContentsAfterFiltering);
  }

  private void doTrimingTest(
      Optional<String> keepResourcePattern,
      String rDotJavaContentsAfterFiltering) throws IOException, InterruptedException {
    ProjectFilesystem filesystem = new ProjectFilesystem(tmpFolder.getRoot());
    BuildRuleResolver resolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);

    AaptPackageResources aaptPackageResources = new AaptPackageResources(
        new FakeBuildRuleParamsBuilder(BuildTargetFactory.newInstance("//:aapt"))
            .setProjectFilesystem(filesystem)
            .build(),
         ruleFinder,
         resolver,
        null,
        new IdentityResourcesProvider(ImmutableList.of()),
        ImmutableList.of(),
        ImmutableSortedSet.of(),
        ImmutableSet.of(),
        Optional.empty(),
        null,
        false,
        false,
        /* includesVectorDrawables */ false,
        EnumSet.noneOf(RType.class),
        null);
    resolver.addToIndex(aaptPackageResources);

    String rDotJavaContents =
        "package com.test;\n" +
        "\n" +
        "public class R {\n" +
        "  public static class string {\n" +
        "    public static final int my_first_resource=0x7f08005c;\n" +
        "    public static final int my_second_resource=0x7f083bc1;\n" +
        "    public static final int keep_resource=0x7f083bc2;\n" +
        "  }\n" +
        "}\n";
    Path rDotJavaPath = aaptPackageResources.getPathToGeneratedRDotJavaSrcFiles()
        .resolve("com/test/R.java");
    filesystem.createParentDirs(rDotJavaPath);
    filesystem.writeContentsToPath(rDotJavaContents, rDotJavaPath);

    DexProducedFromJavaLibrary dexProducedFromJavaLibrary = new DexProducedFromJavaLibrary(
        new FakeBuildRuleParamsBuilder(BuildTargetFactory.newInstance("//:dex"))
            .setProjectFilesystem(filesystem)
            .build(),
        new FakeJavaLibrary(BuildTargetFactory.newInstance("//:lib"), null));
    dexProducedFromJavaLibrary.getBuildOutputInitializer().setBuildOutput(
        dexProducedFromJavaLibrary.initializeFromDisk(new FakeOnDiskBuildInfo()
            .putMetadata(DexProducedFromJavaLibrary.WEIGHT_ESTIMATE, "1")
            .putMetadata(DexProducedFromJavaLibrary.CLASSNAMES_TO_HASHES, "{}")
            .putMetadata(
                DexProducedFromJavaLibrary.REFERENCED_RESOURCES,
                ImmutableList.of("com.test.my_first_resource"))));
    resolver.addToIndex(dexProducedFromJavaLibrary);

    TrimUberRDotJava trimUberRDotJava = new TrimUberRDotJava(
        new FakeBuildRuleParamsBuilder(BuildTargetFactory.newInstance("//:trim"))
            .setProjectFilesystem(filesystem)
            .build(),
        aaptPackageResources,
        ImmutableList.of(dexProducedFromJavaLibrary),
        keepResourcePattern);
    resolver.addToIndex(trimUberRDotJava);

    BuildContext buildContext = FakeBuildContext.withSourcePathResolver(pathResolver);
    BuildableContext buildableContext = new FakeBuildableContext();
    ExecutionContext executionContext = TestExecutionContext.newInstance();
    ImmutableList<Step> steps = trimUberRDotJava.getBuildSteps(buildContext, buildableContext);
    for (Step step : steps) {
      step.execute(executionContext);
    }

    ZipInspector inspector =
        new ZipInspector(pathResolver.getAbsolutePath(trimUberRDotJava.getSourcePathToOutput()));
    inspector.assertFileContents("com/test/R.java", rDotJavaContentsAfterFiltering);
  }
}
