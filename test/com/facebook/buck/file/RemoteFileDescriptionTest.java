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

package com.facebook.buck.file;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.nio.file.Path;
import java.util.Optional;

public class RemoteFileDescriptionTest {

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private Downloader downloader;
  private RemoteFileDescription description;
  private ProjectFilesystem filesystem;
  private BuildRuleResolver ruleResolver;

  @Before
  public void setUp() {
    downloader = new ExplodingDownloader();
    description = new RemoteFileDescription(downloader);
    filesystem = new FakeProjectFilesystem();
    ruleResolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());
  }

  @Test
  public void badSha1HasUseableException() throws NoSuchBuildTargetException {
    BuildTarget target = BuildTargetFactory.newInstance("//cheese:cake");

    RemoteFileDescription.Arg arg = description.createUnpopulatedConstructorArg();
    arg.sha1 = "";

    exception.expect(HumanReadableException.class);
    exception.expectMessage(Matchers.containsString(target.getFullyQualifiedName()));

    description.createBuildRule(
        TargetGraph.EMPTY,
        RemoteFileBuilder.createBuilder(downloader, target)
            .createBuildRuleParams(ruleResolver, filesystem),
        ruleResolver,
        arg);
  }

  @Test
  public void remoteFileBinaryRuleIsCreatedForExecutableType() throws NoSuchBuildTargetException {
    BuildTarget target = BuildTargetFactory.newInstance("//mmmm:kale");

    RemoteFileDescription.Arg arg = description.createUnpopulatedConstructorArg();
    arg.type = Optional.of(RemoteFile.Type.EXECUTABLE);
    arg.sha1 = "cf23df2207d99a74fbe169e3eba035e633b65d94";
    arg.out = Optional.of("kale");

    BuildRule buildRule = description.createBuildRule(
        TargetGraph.EMPTY,
        RemoteFileBuilder.createBuilder(downloader, target)
            .createBuildRuleParams(ruleResolver, filesystem),
        ruleResolver,
        arg);
    ruleResolver.addToIndex(buildRule);

    assertThat(buildRule, CoreMatchers.instanceOf(RemoteFileBinary.class));
    SourcePathResolver pathResolver =
        new SourcePathResolver(new SourcePathRuleFinder(ruleResolver));
    Tool executableCommand = ((RemoteFileBinary) buildRule).getExecutableCommand();
    assertThat(executableCommand.getInputs(), Matchers.hasSize(1));
    SourcePath input = Iterables.getOnlyElement(executableCommand.getInputs());
    Path absolutePath = pathResolver.getAbsolutePath(input);
    assertEquals("kale", absolutePath.getFileName().toString());
    assertEquals(
        ImmutableList.of(absolutePath.toString()),
        executableCommand.getCommandPrefix(pathResolver));
  }
}
