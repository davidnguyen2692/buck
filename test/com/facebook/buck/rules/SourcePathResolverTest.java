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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.facebook.buck.io.ArchiveMemberPath;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.shell.Genrule;
import com.facebook.buck.shell.GenruleBuilder;
import com.facebook.buck.step.Step;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.MoreAsserts;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.collect.ImmutableList;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class SourcePathResolverTest {
  @Rule
  public final ExpectedException exception = ExpectedException.none();


  @Test
  public void resolvePathSourcePath() {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
    ));
    Path expectedPath = Paths.get("foo");
    SourcePath sourcePath = new PathSourcePath(projectFilesystem, expectedPath);

    assertEquals(expectedPath, pathResolver.getRelativePath(sourcePath));
  }

  @Test
  public void resolveBuildTargetSourcePath() {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    Path expectedPath = Paths.get("foo");
    BuildRule rule = new PathReferenceRule(
        new FakeBuildRuleParamsBuilder(BuildTargetFactory.newInstance("//:foo")).build(),
        pathResolver,
        expectedPath);
    resolver.addToIndex(rule);
    SourcePath sourcePath = new BuildTargetSourcePath(rule.getBuildTarget());

    assertEquals(expectedPath, pathResolver.getRelativePath(sourcePath));
  }

  @Test
  public void resolveBuildTargetSourcePathWithOverriddenOutputPath() {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    Path expectedPath = Paths.get("foo");
    FakeBuildRule rule = new FakeBuildRule("//:foo", pathResolver);
    rule.setOutputFile("notfoo");
    resolver.addToIndex(rule);
    SourcePath sourcePath = new BuildTargetSourcePath(rule.getBuildTarget(), expectedPath);

    assertEquals(expectedPath, pathResolver.getRelativePath(sourcePath));
  }

  @Test
  public void getRuleCanGetRuleOfBuildRuleSoucePath() {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);
    FakeBuildRule rule = new FakeBuildRule("//:foo", pathResolver);
    rule.setOutputFile("foo");
    resolver.addToIndex(rule);
    SourcePath sourcePath = new BuildTargetSourcePath(rule.getBuildTarget());

    assertEquals(Optional.of(rule), ruleFinder.getRule(sourcePath));
  }

  @Test
  public void getRuleCannotGetRuleOfPathSoucePath() {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
    );
    SourcePath sourcePath = new PathSourcePath(projectFilesystem, Paths.get("foo"));

    assertEquals(Optional.empty(), ruleFinder.getRule(sourcePath));
  }

  @Test
  public void getRelativePathCanGetRelativePathOfPathSourcePath() {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
    ));
    Path expectedPath = Paths.get("foo");
    SourcePath sourcePath = new PathSourcePath(projectFilesystem, expectedPath);

    assertEquals(expectedPath, pathResolver.getRelativePath(sourcePath));
  }

  @Test
  public void relativePathForABuildTargetSourcePathIsTheRulesOutputPath() {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    FakeBuildRule rule = new FakeBuildRule("//:foo", pathResolver);
    rule.setOutputFile("foo");
    resolver.addToIndex(rule);
    SourcePath sourcePath = new BuildTargetSourcePath(rule.getBuildTarget());

    assertEquals(rule.getPathToOutput(), pathResolver.getRelativePath(sourcePath));
  }

  @Test
  public void testEmptyListAsInputToFilterInputsToCompareToOutput() {
    Iterable<SourcePath> sourcePaths = ImmutableList.of();
    SourcePathResolver resolver = new SourcePathResolver(new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
     ));
    Iterable<Path> inputs = resolver.filterInputsToCompareToOutput(sourcePaths);
    MoreAsserts.assertIterablesEquals(ImmutableList.<String>of(), inputs);
  }

  @Test
  public void testFilterInputsToCompareToOutputExcludesBuildTargetSourcePaths() {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    FakeBuildRule rule = new FakeBuildRule(
        BuildTargetFactory.newInstance("//java/com/facebook:facebook"),
        pathResolver);
    resolver.addToIndex(rule);

    Iterable<? extends SourcePath> sourcePaths = ImmutableList.of(
        new FakeSourcePath("java/com/facebook/Main.java"),
        new FakeSourcePath("java/com/facebook/BuckConfig.java"),
        new BuildTargetSourcePath(rule.getBuildTarget()));
    Iterable<Path> inputs = pathResolver.filterInputsToCompareToOutput(sourcePaths);
    MoreAsserts.assertIterablesEquals(
        "Iteration order should be preserved: results should not be alpha-sorted.",
        ImmutableList.of(
            Paths.get("java/com/facebook/Main.java"),
            Paths.get("java/com/facebook/BuckConfig.java")),
        inputs);
  }

  @Test
  public void testFilterBuildRuleInputsExcludesPathSourcePaths() {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);
    FakeBuildRule rule = new FakeBuildRule(
        BuildTargetFactory.newInstance("//java/com/facebook:facebook"),
        pathResolver);
    resolver.addToIndex(rule);
    FakeBuildRule rule2 = new FakeBuildRule(
        BuildTargetFactory.newInstance("//bar:foo"),
        pathResolver);
    resolver.addToIndex(rule2);

    Iterable<? extends SourcePath> sourcePaths = ImmutableList.of(
        new FakeSourcePath("java/com/facebook/Main.java"),
        new BuildTargetSourcePath(rule.getBuildTarget()),
        new FakeSourcePath("java/com/facebook/BuckConfig.java"),
        new BuildTargetSourcePath(rule2.getBuildTarget()));
    Iterable<BuildRule> inputs = ruleFinder.filterBuildRuleInputs(sourcePaths);
    MoreAsserts.assertIterablesEquals(
        "Iteration order should be preserved: results should not be alpha-sorted.",
        ImmutableList.of(
            rule,
            rule2),
        inputs);
  }

  @Test
  public void getSourcePathNameOnPathSourcePath() {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    Path path = Paths.get("hello/world.txt");

    // Test that constructing a PathSourcePath without an explicit name resolves to the
    // string representation of the path.
    PathSourcePath pathSourcePath1 = new PathSourcePath(projectFilesystem, path);
    SourcePathResolver resolver = new SourcePathResolver(new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
     ));
    String actual1 = resolver.getSourcePathName(
        BuildTargetFactory.newInstance("//:test"),
        pathSourcePath1);
    assertEquals(path.toString(), actual1);
  }

  @Test
  public void getSourcePathNameOnBuildTargetSourcePath() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));

    // Verify that wrapping a genrule in a BuildTargetSourcePath resolves to the output name of
    // that genrule.
    String out = "test/blah.txt";
    Genrule genrule = GenruleBuilder
        .newGenruleBuilder(BuildTargetFactory.newInstance("//:genrule"))
        .setOut(out)
        .build(resolver);
    BuildTargetSourcePath buildTargetSourcePath1 = new BuildTargetSourcePath(
        genrule.getBuildTarget());
    String actual1 = pathResolver.getSourcePathName(
        BuildTargetFactory.newInstance("//:test"),
        buildTargetSourcePath1);
    assertEquals(out, actual1);

    // Test that using other BuildRule types resolves to the short name.
    BuildTarget fakeBuildTarget = BuildTargetFactory.newInstance("//:fake");
    FakeBuildRule fakeBuildRule = new FakeBuildRule(
        new FakeBuildRuleParamsBuilder(fakeBuildTarget).build(),
        pathResolver);
    resolver.addToIndex(fakeBuildRule);
    BuildTargetSourcePath buildTargetSourcePath2 = new BuildTargetSourcePath(
        fakeBuildRule.getBuildTarget());
    String actual2 = pathResolver.getSourcePathName(
        BuildTargetFactory.newInstance("//:test"),
        buildTargetSourcePath2);
    assertEquals(fakeBuildTarget.getShortName(), actual2);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getSourcePathNameOnArchiveMemberSourcePath() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));

    String out = "test/blah.jar";
    Genrule genrule = GenruleBuilder
        .newGenruleBuilder(BuildTargetFactory.newInstance("//:genrule"))
        .setOut(out)
        .build(resolver);
    BuildTargetSourcePath archiveSourcePath = new BuildTargetSourcePath(
        genrule.getBuildTarget());

    ArchiveMemberSourcePath archiveMemberSourcePath =
        new ArchiveMemberSourcePath(archiveSourcePath, Paths.get("member"));

    pathResolver.getSourcePathName(null, archiveMemberSourcePath);
  }

  @Test
  public void getSourcePathNamesThrowsOnDuplicates() {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    BuildTarget target = BuildTargetFactory.newInstance("//:test");
    String parameter = "srcs";
    PathSourcePath pathSourcePath1 = new PathSourcePath(projectFilesystem, Paths.get("same_name"));
    PathSourcePath pathSourcePath2 = new PathSourcePath(projectFilesystem, Paths.get("same_name"));

    // Try to resolve these source paths, with the same name, together and verify that an
    // exception is thrown.
    try {
      SourcePathResolver resolver =
          new SourcePathResolver(new SourcePathRuleFinder(
              new BuildRuleResolver(
                  TargetGraph.EMPTY,
                  new DefaultTargetNodeToBuildRuleTransformer())));
      resolver.getSourcePathNames(
          target,
          parameter,
          ImmutableList.of(pathSourcePath1, pathSourcePath2));
      fail("expected to throw");
    } catch (HumanReadableException e) {
      assertTrue(e.getMessage().contains("duplicate entries"));
    }
  }

  @Test
  public void getSourcePathNameExplicitPath() {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    BuildRule rule = resolver.addToIndex(new FakeBuildRule("//foo:bar", pathResolver));
    assertThat(
        pathResolver.getSourcePathName(
            rule.getBuildTarget(),
            new BuildTargetSourcePath(
                rule.getBuildTarget(),
                filesystem.getBuckPaths().getGenDir().resolve("foo").resolve("something.cpp"))),
        Matchers.equalTo("something.cpp"));
  }

  @Test
  public void getSourcePathNamesWithExplicitPathsAvoidesDuplicates() {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    BuildRule rule = resolver.addToIndex(new FakeBuildRule("//foo:bar", pathResolver));
    SourcePath sourcePath1 =
        new BuildTargetSourcePath(
            rule.getBuildTarget(),
            filesystem.getBuckPaths().getGenDir().resolve("foo").resolve("name1"));
    SourcePath sourcePath2 =
        new BuildTargetSourcePath(
            rule.getBuildTarget(),
            filesystem.getBuckPaths().getGenDir().resolve("foo").resolve("name2"));
    pathResolver.getSourcePathNames(
        rule.getBuildTarget(),
        "srcs",
        ImmutableList.of(sourcePath1, sourcePath2));
  }

  @Test(expected = IllegalStateException.class)
  public void getRelativePathCanOnlyReturnARelativePath() {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));

    PathSourcePath path = new PathSourcePath(
        new FakeProjectFilesystem(),
        Paths.get("cheese").toAbsolutePath());

    pathResolver.getRelativePath(path);
  }

  @Test
  public void testGetRelativePathForArchiveMemberSourcePath() {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));

    BuildRule rule = resolver.addToIndex(new FakeBuildRule("//foo:bar", pathResolver));
    Path archivePath = filesystem.getBuckPaths().getGenDir().resolve("foo.jar");
    SourcePath archiveSourcePath =
        new BuildTargetSourcePath(
            rule.getBuildTarget(),
            archivePath);
    Path memberPath = Paths.get("foo.class");

    ArchiveMemberSourcePath path = new ArchiveMemberSourcePath(archiveSourcePath, memberPath);

    ArchiveMemberPath relativePath = pathResolver.getRelativeArchiveMemberPath(path);
    assertEquals(archivePath, relativePath.getArchivePath());
    assertEquals(memberPath, relativePath.getMemberPath());
  }

  @Test
  public void testGetAbsolutePathForArchiveMemberSourcePath() {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));

    BuildRule rule = resolver.addToIndex(
        new FakeBuildRule(BuildTargetFactory.newInstance("//foo:bar"), filesystem, pathResolver));
    Path archivePath = filesystem.getBuckPaths().getGenDir().resolve("foo.jar");
    Path archiveAbsolutePath = filesystem.resolve(archivePath);
    SourcePath archiveSourcePath =
        new BuildTargetSourcePath(
            rule.getBuildTarget(),
            archivePath);
    Path memberPath = Paths.get("foo.class");

    ArchiveMemberSourcePath path = new ArchiveMemberSourcePath(archiveSourcePath, memberPath);

    ArchiveMemberPath absolutePath = pathResolver.getAbsoluteArchiveMemberPath(path);
    assertEquals(archiveAbsolutePath, absolutePath.getArchivePath());
    assertEquals(memberPath, absolutePath.getMemberPath());
  }

  private static class PathReferenceRule extends AbstractBuildRuleWithResolver {

    private final Path source;

    protected PathReferenceRule(
        BuildRuleParams buildRuleParams,
        SourcePathResolver resolver,
        Path source) {
      super(buildRuleParams, resolver);
      this.source = source;
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext context, BuildableContext buildableContext) {
      return ImmutableList.of();
    }

    @Override
    public Path getPathToOutput() {
      return source;
    }
  }
}
