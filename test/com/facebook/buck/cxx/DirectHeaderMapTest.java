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

package com.facebook.buck.cxx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.facebook.buck.io.MorePaths;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.keys.DefaultRuleKeyFactory;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.facebook.buck.testutil.FakeFileHashCache;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DirectHeaderMapTest {

  @Rule
  public final TemporaryPaths tmpDir = new TemporaryPaths();

  private ProjectFilesystem projectFilesystem;
  private BuildTarget buildTarget;
  private DirectHeaderMap buildRule;
  private BuildRuleResolver ruleResolver;
  private SourcePathResolver pathResolver;
  private ImmutableMap<Path, SourcePath> links;
  private Path symlinkTreeRoot;
  private Path headerMapPath;
  private Path file1;
  private Path file2;
  private SourcePathRuleFinder ruleFinder;

  @Before
  public void setUp() throws Exception {
    projectFilesystem = new FakeProjectFilesystem(tmpDir.getRoot());

    // Create a build target to use when building the symlink tree.
    buildTarget = BuildTargetFactory.newInstance("//test:test");

    // Get the first file we're symlinking
    Path link1 = Paths.get("file");
    file1 = tmpDir.newFile();
    Files.write(file1, "hello world".getBytes(Charsets.UTF_8));

    // Get the second file we're symlinking
    Path link2 = Paths.get("directory", "then", "file");
    file2 = tmpDir.newFile();
    Files.write(file2, "hello world".getBytes(Charsets.UTF_8));

    // Setup the map representing the link tree.
    links = ImmutableMap.of(
        link1,
        new PathSourcePath(
            projectFilesystem,
            MorePaths.relativize(tmpDir.getRoot(), file1)),
        link2,
        new PathSourcePath(
            projectFilesystem,
            MorePaths.relativize(tmpDir.getRoot(), file2)));

    // The output path used by the buildable for the link tree.
    symlinkTreeRoot =
        BuildTargets.getGenPath(projectFilesystem, buildTarget, "%s/symlink-tree-root");

    // Setup the symlink tree buildable.
    ruleResolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());

    ruleFinder = new SourcePathRuleFinder(ruleResolver);
    pathResolver = new SourcePathResolver(ruleFinder);

    buildRule = new DirectHeaderMap(
        new FakeBuildRuleParamsBuilder(buildTarget).build(),
        symlinkTreeRoot,
        links,
        ruleFinder);
    ruleResolver.addToIndex(buildRule);

    headerMapPath = pathResolver.getRelativePath(buildRule.getSourcePathToOutput());
  }

  @Test
  public void testBuildSteps() throws IOException {
    BuildContext buildContext = FakeBuildContext.withSourcePathResolver(pathResolver);
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    FakeBuildableContext buildableContext = new FakeBuildableContext();

    ImmutableList<Step> expectedBuildSteps =
        ImmutableList.of(
            new MkdirStep(filesystem, headerMapPath.getParent()),
            new RmStep(filesystem, headerMapPath),
            new HeaderMapStep(
                filesystem,
                headerMapPath,
                ImmutableMap.of(
                    Paths.get("file"),
                    filesystem.resolve(filesystem.getBuckPaths().getBuckOut())
                        .relativize(file1),
                    Paths.get("directory/then/file"),
                    filesystem.resolve(filesystem.getBuckPaths().getBuckOut())
                        .relativize(file2))));
    ImmutableList<Step> actualBuildSteps =
        buildRule.getBuildSteps(
            buildContext,
            buildableContext);
    assertEquals(expectedBuildSteps, actualBuildSteps.subList(1, actualBuildSteps.size()));
  }

  @Test
  public void testSymlinkTreeRuleKeyChangesIfLinkMapChanges() throws Exception {
    Path aFile = tmpDir.newFile();
    Files.write(aFile, "hello world".getBytes(Charsets.UTF_8));
    DirectHeaderMap modifiedBuildRule = new DirectHeaderMap(
        new FakeBuildRuleParamsBuilder(buildTarget).build(),
        symlinkTreeRoot,
        ImmutableMap.of(
            Paths.get("different/link"),
            new PathSourcePath(
                projectFilesystem,
                MorePaths.relativize(tmpDir.getRoot(), aFile))),
        ruleFinder);

    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
    );
    SourcePathResolver resolver = new SourcePathResolver(ruleFinder);

    // Calculate their rule keys and verify they're different.
    FakeFileHashCache hashCache = FakeFileHashCache.createFromStrings(
        ImmutableMap.of());
    RuleKey key1 = new DefaultRuleKeyFactory(0, hashCache, resolver, ruleFinder).build(
        buildRule);
    RuleKey key2 = new DefaultRuleKeyFactory(0, hashCache, resolver, ruleFinder).build(
        modifiedBuildRule);
    assertNotEquals(key1, key2);
  }

  @Test
  public void testRuleKeyDoesNotChangeIfLinkTargetsChange() throws IOException {
    BuildRuleResolver ruleResolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());
    ruleResolver.addToIndex(buildRule);
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(ruleResolver);
    SourcePathResolver resolver = new SourcePathResolver(ruleFinder);

    DefaultRuleKeyFactory defaultRuleKeyFactory = new DefaultRuleKeyFactory(
        0,
        FakeFileHashCache.createFromStrings(
            ImmutableMap.of()),
        resolver,
        ruleFinder);

    // Calculate the rule key
    RuleKey key1 = defaultRuleKeyFactory.build(buildRule);

    // Change the contents of the target of the link.
    Path existingFile = resolver.getAbsolutePath(links.values().asList().get(0));
    Files.write(existingFile, "something new".getBytes(Charsets.UTF_8));

    // Re-calculate the rule key
    RuleKey key2 = defaultRuleKeyFactory.build(buildRule);

    // Verify that the rules keys are the same.
    assertEquals(key1, key2);
  }

}
