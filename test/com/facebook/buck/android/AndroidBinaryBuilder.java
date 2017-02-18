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

package com.facebook.buck.android;

import static com.facebook.buck.jvm.java.JavaCompilationConstants.ANDROID_JAVAC_OPTIONS;
import static com.facebook.buck.jvm.java.JavaCompilationConstants.DEFAULT_JAVA_OPTIONS;

import com.facebook.buck.android.FilterResourcesStep.ResourceFilter;
import com.facebook.buck.android.ResourcesFilter.ResourceCompressionMode;
import com.facebook.buck.android.aapt.RDotTxtEntry;
import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.cxx.CxxPlatformUtils;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.SourcePath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class AndroidBinaryBuilder extends
    AbstractNodeBuilder<AndroidBinaryDescription.Arg, AndroidBinaryDescription, AndroidBinary> {

  private AndroidBinaryBuilder(BuildTarget target) {
    super(
        new AndroidBinaryDescription(
            DEFAULT_JAVA_OPTIONS,
            ANDROID_JAVAC_OPTIONS,
            new ProGuardConfig(FakeBuckConfig.builder().build()),
            ImmutableMap.of(),
            MoreExecutors.newDirectExecutorService(),
            CxxPlatformUtils.DEFAULT_CONFIG),
        target);
  }

  public static AndroidBinaryBuilder createBuilder(BuildTarget buildTarget) {
    return new AndroidBinaryBuilder(buildTarget);
  }

  public AndroidBinaryBuilder setManifest(SourcePath manifest) {
    arg.manifest = manifest;
    return this;
  }

  public AndroidBinaryBuilder setOriginalDeps(ImmutableSortedSet<BuildTarget> originalDeps) {
    arg.deps = originalDeps;
    return this;
  }

  public AndroidBinaryBuilder setKeystore(BuildTarget keystore) {
    arg.keystore = keystore;
    amend(arg.deps, keystore);
    return this;
  }

  public AndroidBinaryBuilder setPackageType(String packageType) {
    arg.packageType = Optional.of(packageType);
    return this;
  }

  public AndroidBinaryBuilder setShouldSplitDex(boolean shouldSplitDex) {
    arg.useSplitDex = Optional.of(shouldSplitDex);
    return this;
  }

  public AndroidBinaryBuilder setDexCompression(DexStore dexStore) {
    arg.dexCompression = Optional.of(dexStore);
    return this;
  }

  public AndroidBinaryBuilder setLinearAllocHardLimit(long limit) {
    arg.linearAllocHardLimit = Optional.of(limit);
    return this;
  }

  public AndroidBinaryBuilder setPrimaryDexScenarioOverflowAllowed(boolean allowed) {
    arg.primaryDexScenarioOverflowAllowed = Optional.of(allowed);
    return this;
  }

  public AndroidBinaryBuilder setBuildTargetsToExcludeFromDex(
      Set<BuildTarget> buildTargetsToExcludeFromDex) {
    arg.noDx = Optional.of(buildTargetsToExcludeFromDex);
    return this;
  }

  public AndroidBinaryBuilder setResourceCompressionMode(
      ResourceCompressionMode resourceCompressionMode) {
    arg.resourceCompression = Optional.of(resourceCompressionMode.toString());
    return this;
  }

  public AndroidBinaryBuilder setResourceFilter(ResourceFilter resourceFilter) {
    List<String> rawFilters = ImmutableList.copyOf(resourceFilter.getFilter());
    arg.resourceFilter = rawFilters;
    return this;
  }

  public AndroidBinaryBuilder setIntraDexReorderResources(boolean enableReorder,
      SourcePath reorderTool,
      SourcePath reorderData) {
    arg.reorderClassesIntraDex = Optional.of(enableReorder);
    arg.dexReorderToolFile = Optional.of(reorderTool);
    arg.dexReorderDataDumpFile = Optional.of(reorderData);
    return this;
  }

  public AndroidBinaryBuilder setNoDx(Set<BuildTarget> noDx) {
    arg.noDx = Optional.of(noDx);
    return this;
  }

  public AndroidBinaryBuilder setDuplicateResourceBehavior(
      AndroidBinaryDescription.Arg.DuplicateResourceBehaviour value) {
    arg.duplicateResourceBehavior = value;
    return this;
  }

  public AndroidBinaryBuilder setBannedDuplicateResourceTypes(Set<RDotTxtEntry.RType> value) {
    arg.bannedDuplicateResourceTypes = value;
    return this;
  }

  public AndroidBinaryBuilder setAllowedDuplicateResourceTypes(Set<RDotTxtEntry.RType> value) {
    arg.allowedDuplicateResourceTypes = value;
    return this;
  }
}
