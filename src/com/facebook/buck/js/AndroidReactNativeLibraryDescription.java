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

package com.facebook.buck.js;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.Hint;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePaths;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;

import java.util.Collections;
import java.util.Optional;

public class AndroidReactNativeLibraryDescription
    implements
    Description<AndroidReactNativeLibraryDescription.Args>,
    Flavored,
    ImplicitDepsInferringDescription<AndroidReactNativeLibraryDescription.Args> {

  private final ReactNativeLibraryGraphEnhancer enhancer;
  private final Supplier<SourcePath> packager;

  public AndroidReactNativeLibraryDescription(final ReactNativeBuckConfig buckConfig) {
    this.enhancer = new ReactNativeLibraryGraphEnhancer(buckConfig);
    this.packager = buckConfig::getPackagerSourcePath;
  }

  @Override
  public Args createUnpopulatedConstructorArg() {
    return new Args();
  }

  @Override
  public <A extends Args> AndroidReactNativeLibrary createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) {
    return enhancer.enhanceForAndroid(params, resolver, args);
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    return ReactNativeFlavors.validateFlavors(flavors);
  }

  @Override
  public Iterable<BuildTarget> findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      Args constructorArg) {
    return SourcePaths.filterBuildTargetSourcePaths(Collections.singleton(packager.get()));
  }

  @SuppressFieldNotInitialized
  public static class Args extends ReactNativeLibraryArgs {
    @Hint(name = "package")
    public Optional<String> rDotJavaPackage;
  }

}
