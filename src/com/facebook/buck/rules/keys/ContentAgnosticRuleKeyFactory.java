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

package com.facebook.buck.rules.keys;

import com.facebook.buck.hashing.FileHashLoader;
import com.facebook.buck.io.ArchiveMemberPath;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.google.common.hash.HashCode;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A factory for generating {@link RuleKey}s that only take into the account the path of a file
 * and not the contents(hash) of the file.
 */
public class ContentAgnosticRuleKeyFactory implements RuleKeyFactory<RuleKey> {

  private final RuleKeyFieldLoader ruleKeyFieldLoader;
  private final SourcePathResolver pathResolver;
  private final SourcePathRuleFinder ruleFinder;

  private final FileHashLoader fileHashLoader =
      new FileHashLoader() {

        @Override
        public HashCode get(Path path) throws IOException {
          return HashCode.fromLong(0);
        }

        @Override
        public long getSize(Path path) throws IOException {
          return 0;
        }

        @Override
        public HashCode get(ArchiveMemberPath archiveMemberPath) throws IOException {
          throw new AssertionError();
        }

      };

  private final SingleBuildRuleKeyCache<RuleKey> ruleKeyCache = new SingleBuildRuleKeyCache<>();

  public ContentAgnosticRuleKeyFactory(
      RuleKeyFieldLoader ruleKeyFieldLoader,
      SourcePathResolver pathResolver,
      SourcePathRuleFinder ruleFinder) {
    this.ruleKeyFieldLoader = ruleKeyFieldLoader;
    this.pathResolver = pathResolver;
    this.ruleFinder = ruleFinder;
  }

  private RuleKey calculateBuildRuleKey(BuildRule buildRule) {
    RuleKeyBuilder<RuleKey> builder = newBuilder();
    ruleKeyFieldLoader.setFields(buildRule, builder);
    return builder.build();
  }

  @Override
  public RuleKey build(BuildRule buildRule) {
    return ruleKeyCache.get(buildRule, this::calculateBuildRuleKey);
  }

  private RuleKeyBuilder<RuleKey> newBuilder() {
    return new RuleKeyBuilder<RuleKey>(ruleFinder, pathResolver, fileHashLoader) {

      private RuleKey calculateRuleKeyAppendableKey(RuleKeyAppendable appendable) {
        RuleKeyBuilder<RuleKey> subKeyBuilder = newBuilder();
        appendable.appendToRuleKey(subKeyBuilder);
        return subKeyBuilder.build();
      }

      @Override
      protected RuleKeyBuilder<RuleKey> setBuildRule(BuildRule rule) {
        return setBuildRuleKey(ContentAgnosticRuleKeyFactory.this.build(rule));
      }

      @Override
      protected RuleKeyBuilder<RuleKey> setAppendableRuleKey(RuleKeyAppendable appendable) {
        return setAppendableRuleKey(
            ruleKeyCache.get(appendable, this::calculateRuleKeyAppendableKey));
      }

      @Override
      protected RuleKeyBuilder<RuleKey> setSourcePath(SourcePath sourcePath) throws IOException {
        if (sourcePath instanceof BuildTargetSourcePath) {
          return setSourcePathAsRule((BuildTargetSourcePath) sourcePath);
        } else {
          return setSourcePathDirectly(sourcePath);
        }
      }

      @Override
      public RuleKey build() {
        return buildRuleKey();
      }

    };
  }
}
