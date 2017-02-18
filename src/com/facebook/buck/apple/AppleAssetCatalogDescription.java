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

package com.facebook.buck.apple;

import com.facebook.buck.rules.AbstractDescriptionArg;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.NoopBuildRule;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;

import java.util.Optional;
import java.util.SortedSet;

/**
 * Description for an apple_asset_catalog rule, which identifies an asset
 * catalog for an iOS or Mac OS X library or binary.
 */
public class AppleAssetCatalogDescription implements Description<AppleAssetCatalogDescription.Arg> {

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> NoopBuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) {
    return new NoopBuildRule(params, new SourcePathResolver(new SourcePathRuleFinder(resolver)));
  }

  public enum Optimization {
    SPACE("space"),
    TIME("time"),
    ;

    private final String argument;

    Optimization(String argument) {
      this.argument = argument;
    }

    public String toArgument() {
      return argument;
    }
  }

  @SuppressFieldNotInitialized
  public static class Arg extends AbstractDescriptionArg {
    public SortedSet<SourcePath> dirs;
    public Optional<String> appIcon;
    public Optional<String> launchImage;
    public Optimization optimization = Optimization.SPACE;
  }
}
