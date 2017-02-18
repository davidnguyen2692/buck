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

package com.facebook.buck.cxx;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.rules.coercer.VersionMatchedCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

import java.util.Optional;
import java.util.regex.Pattern;

public class PrebuiltCxxLibraryBuilder extends AbstractCxxBuilder<
    PrebuiltCxxLibraryDescription.Arg,
    PrebuiltCxxLibraryDescription,
    BuildRule> {

  public PrebuiltCxxLibraryBuilder(
      BuildTarget target,
      FlavorDomain<CxxPlatform> cxxPlatforms) {
    super(new PrebuiltCxxLibraryDescription(CxxPlatformUtils.DEFAULT_CONFIG, cxxPlatforms), target);
  }

  public PrebuiltCxxLibraryBuilder(BuildTarget target) {
    this(target, createDefaultPlatforms());
  }

  public PrebuiltCxxLibraryBuilder setIncludeDirs(ImmutableList<String> includeDirs) {
    arg.includeDirs = includeDirs;
    return this;
  }

  public PrebuiltCxxLibraryBuilder setLibName(String libName) {
    arg.libName = Optional.of(libName);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setLibDir(String libDir) {
    arg.libDir = Optional.of(libDir);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setLinkWithoutSoname(boolean linkWithoutSoname) {
    arg.linkWithoutSoname = linkWithoutSoname;
    return this;
  }

  public PrebuiltCxxLibraryBuilder setExportedHeaders(SourceList exportedHeaders) {
    arg.exportedHeaders = exportedHeaders;
    return this;
  }

  public PrebuiltCxxLibraryBuilder setExportedPlatformHeaders(
      PatternMatchedCollection<SourceList> collection) {
    arg.exportedPlatformHeaders = collection;
    return this;
  }

  public PrebuiltCxxLibraryBuilder setHeaderNamespace(String headerNamespace) {
    arg.headerNamespace = Optional.of(headerNamespace);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setHeaderOnly(boolean headerOnly) {
    arg.headerOnly = Optional.of(headerOnly);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setProvided(boolean provided) {
    arg.provided = provided;
    return this;
  }

  public PrebuiltCxxLibraryBuilder setExportedLinkerFlags(ImmutableList<String> linkerFlags) {
    arg.exportedLinkerFlags = linkerFlags;
    return this;
  }

  public PrebuiltCxxLibraryBuilder setSoname(String soname) {
    arg.soname = Optional.of(soname);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setDeps(ImmutableSortedSet<BuildTarget> deps) {
    arg.deps = deps;
    return this;
  }

  public PrebuiltCxxLibraryBuilder setForceStatic(boolean forceStatic) {
    arg.forceStatic = Optional.of(forceStatic);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setExportedDeps(ImmutableSortedSet<BuildTarget> exportedDeps) {
    arg.exportedDeps = exportedDeps;
    return this;
  }

  public PrebuiltCxxLibraryBuilder setSupportedPlatformsRegex(Pattern supportedPlatformsRegex) {
    arg.supportedPlatformsRegex = Optional.of(supportedPlatformsRegex);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setVersionedSubDir(
      VersionMatchedCollection<String> versionedSubDir) {
    arg.versionedSubDir = Optional.of(versionedSubDir);
    return this;
  }

}
