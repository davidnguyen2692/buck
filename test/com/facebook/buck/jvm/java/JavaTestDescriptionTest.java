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

package com.facebook.buck.jvm.java;

import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.FakeExportDependenciesRule;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.google.common.collect.ImmutableSortedSet;

import org.hamcrest.Matchers;
import org.junit.Test;

public class JavaTestDescriptionTest {

  @Test
  public void rulesExportedFromDepsBecomeFirstOrderDeps() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));

    FakeBuildRule exportedRule =
        resolver.addToIndex(new FakeBuildRule("//:exported_rule", pathResolver));
    FakeExportDependenciesRule exportingRule =
        resolver.addToIndex(
            new FakeExportDependenciesRule("//:exporting_rule", pathResolver, exportedRule));

    BuildTarget target = BuildTargetFactory.newInstance("//:rule");
    JavaTest javaTest = JavaTestBuilder.createBuilder(target)
        .addDep(exportingRule.getBuildTarget())
        .build(resolver);

    ImmutableSortedSet<BuildRule> deps = javaTest.getCompiledTestsLibrary().getDeps();
    assertThat(deps, Matchers.<BuildRule>hasItem(exportedRule));
  }

  @Test
  public void rulesExportedFromProvidedDepsBecomeFirstOrderDeps() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));

    FakeBuildRule exportedRule =
        resolver.addToIndex(new FakeBuildRule("//:exported_rule", pathResolver));
    FakeExportDependenciesRule exportingRule =
        resolver.addToIndex(
            new FakeExportDependenciesRule("//:exporting_rule", pathResolver, exportedRule));

    BuildTarget target = BuildTargetFactory.newInstance("//:rule");
    JavaTest javaTest = JavaTestBuilder.createBuilder(target)
        .addProvidedDep(exportingRule.getBuildTarget())
        .build(resolver);

    ImmutableSortedSet<BuildRule> deps = javaTest.getCompiledTestsLibrary().getDeps();
    assertThat(deps, Matchers.<BuildRule>hasItem(exportedRule));
  }

}
