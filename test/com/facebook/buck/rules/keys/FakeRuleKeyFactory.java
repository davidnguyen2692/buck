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

package com.facebook.buck.rules.keys;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.util.cache.FileHashCache;
import com.facebook.buck.util.cache.NullFileHashCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;

import javax.annotation.Nullable;

public class FakeRuleKeyFactory
    implements RuleKeyFactory<RuleKey>, DependencyFileRuleKeyFactory {

  private final ImmutableMap<BuildTarget, RuleKey> ruleKeys;
  private final FileHashCache fileHashCache;

  public FakeRuleKeyFactory(
      ImmutableMap<BuildTarget, RuleKey> ruleKeys,
      FileHashCache fileHashCache) {
    this.ruleKeys = ruleKeys;
    this.fileHashCache = fileHashCache;
  }

  public FakeRuleKeyFactory(ImmutableMap<BuildTarget, RuleKey> ruleKeys) {
    this(ruleKeys, new NullFileHashCache());
  }

  private RuleKeyBuilder<RuleKey> newInstance(final BuildRule buildRule) {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())
    );
    SourcePathResolver resolver = new SourcePathResolver(ruleFinder);
    return new UncachedRuleKeyBuilder(ruleFinder, resolver, fileHashCache, this) {

      @Override
      protected RuleKeyBuilder<RuleKey> setReflectively(@Nullable Object val) {
        return this;
      }

      @Override
      public RuleKey build() {
        return ruleKeys.get(buildRule.getBuildTarget());
      }

    };
  }

  @Override
  public RuleKey build(BuildRule buildRule) {
    return newInstance(buildRule).build();
  }

  @Override
  public RuleKeyAndInputs build(
      SupportsDependencyFileRuleKey rule,
      ImmutableList<DependencyFileEntry> inputs) throws IOException {
    return RuleKeyAndInputs.of(build(rule), ImmutableSet.<SourcePath>of());
  }

  @Override
  public RuleKeyAndInputs buildManifestKey(
      SupportsDependencyFileRuleKey rule) {
    return RuleKeyAndInputs.of(build(rule), ImmutableSet.<SourcePath>of());
  }

}
