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

package com.facebook.buck.shell;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import org.easymock.EasyMockSupport;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

public class ShTestTest extends EasyMockSupport {

  @After
  public void tearDown() {
    // I don't understand why EasyMockSupport doesn't do this by default.
    verifyAll();
  }

  @Test
  public void testHasTestResultFiles() throws IOException {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();

    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(
        new BuildRuleResolver(
            TargetGraph.EMPTY,
            new DefaultTargetNodeToBuildRuleTransformer())
    );
    ShTest shTest = new ShTest(
        new FakeBuildRuleParamsBuilder("//test/com/example:my_sh_test")
            .setProjectFilesystem(filesystem)
            .build(),
        new SourcePathResolver(ruleFinder),
        ruleFinder,
        new FakeSourcePath("run_test.sh"),
        /* args */ ImmutableList.of(),
        /* env */ ImmutableMap.of(),
        /* resources */ ImmutableSortedSet.of(),
        Optional.empty(),
        /* runTestSeparately */ false,
        /* labels */ ImmutableSet.of(),
        /* contacts */ ImmutableSet.of());
    filesystem.touch(shTest.getPathToTestOutputResult());

    assertTrue(
        "hasTestResultFiles() should return true if result.json exists.",
        shTest.hasTestResultFiles());
  }

  @Test
  public void depsAreRuntimeDeps() {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);

    BuildRule extraDep = new FakeBuildRule("//:extra_dep", pathResolver);
    BuildRule dep = new FakeBuildRule("//:dep", pathResolver);

    BuildTarget target = BuildTargetFactory.newInstance("//:rule");
    ShTest shTest = new ShTest(
        new FakeBuildRuleParamsBuilder(target)
            .setDeclaredDeps(ImmutableSortedSet.of(dep))
            .setExtraDeps(ImmutableSortedSet.of(extraDep))
            .build(),
        pathResolver,
        ruleFinder,
        new FakeSourcePath("run_test.sh"),
        /* args */ ImmutableList.of(),
        /* env */ ImmutableMap.of(),
        /* resources */ ImmutableSortedSet.of(),
        Optional.empty(),
        /* runTestSeparately */ false,
        /* labels */ ImmutableSet.of(),
        /* contacts */ ImmutableSet.of());

    assertThat(
        shTest.getRuntimeDeps().collect(MoreCollectors.toImmutableSet()),
        containsInAnyOrder(
            dep.getBuildTarget(),
            extraDep.getBuildTarget()));
  }

}
