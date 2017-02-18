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

import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;

import java.util.Optional;
import java.util.regex.Pattern;

public class CxxLibraryBuilder extends AbstractCxxSourceBuilder<
    CxxLibraryDescription.Arg,
    CxxLibraryDescription,
    BuildRule,
    CxxLibraryBuilder> {

  public CxxLibraryBuilder(
      BuildTarget target,
      CxxBuckConfig cxxBuckConfig,
      FlavorDomain<CxxPlatform> cxxPlatforms) {
    super(
        new CxxLibraryDescription(
            cxxBuckConfig,
            CxxPlatformUtils.build(cxxBuckConfig),
            new InferBuckConfig(FakeBuckConfig.builder().build()),
            cxxPlatforms),
        target);
  }

  public CxxLibraryBuilder(BuildTarget target, CxxBuckConfig cxxBuckConfig) {
    this(target, cxxBuckConfig, createDefaultPlatforms());
  }

  public CxxLibraryBuilder(BuildTarget target) {
    this(target, createDefaultConfig(), createDefaultPlatforms());
  }

  public CxxLibraryBuilder setExportedHeaders(ImmutableSortedSet<SourcePath> headers)  {
    arg.exportedHeaders = SourceList.ofUnnamedSources(headers);
    return this;
  }

  public CxxLibraryBuilder setExportedHeaders(ImmutableSortedMap<String, SourcePath> headers)  {
    arg.exportedHeaders = SourceList.ofNamedSources(headers);
    return this;
  }

  public CxxLibraryBuilder setExportedHeaders(SourceList headers)  {
    arg.exportedHeaders = headers;
    return this;
  }

  public CxxLibraryBuilder setExportedPreprocessorFlags(
      ImmutableList<String> exportedPreprocessorFlags) {
    arg.exportedPreprocessorFlags = exportedPreprocessorFlags;
    return getThis();
  }

  public CxxLibraryBuilder setExportedPlatformPreprocessorFlags(
      PatternMatchedCollection<ImmutableList<String>> exportedPlatformPreprocessorFlags) {
    arg.exportedPlatformPreprocessorFlags = exportedPlatformPreprocessorFlags;
    return this;
  }

  public CxxLibraryBuilder setExportedLinkerFlags(
      ImmutableList<StringWithMacros> exportedLinkerFlags) {
    arg.exportedLinkerFlags = exportedLinkerFlags;
    return this;
  }

  public CxxLibraryBuilder setExportedPlatformLinkerFlags(
      PatternMatchedCollection<ImmutableList<StringWithMacros>> exportedPlatformLinkerFlags) {
    arg.exportedPlatformLinkerFlags = exportedPlatformLinkerFlags;
    return this;
  }

  public CxxLibraryBuilder setSoname(String soname) {
    arg.soname = Optional.of(soname);
    return this;
  }

  public CxxLibraryBuilder setLinkWhole(boolean linkWhole) {
    arg.linkWhole = Optional.of(linkWhole);
    return this;
  }

  public CxxLibraryBuilder setForceStatic(boolean forceStatic) {
    arg.forceStatic = Optional.of(forceStatic);
    return this;
  }

  public CxxLibraryBuilder setPreferredLinkage(NativeLinkable.Linkage linkage) {
    arg.preferredLinkage = Optional.of(linkage);
    return this;
  }

  public CxxLibraryBuilder setSupportedPlatformsRegex(Pattern regex) {
    arg.supportedPlatformsRegex = Optional.of(regex);
    return this;
  }

  public CxxLibraryBuilder setExportedDeps(ImmutableSortedSet<BuildTarget> exportedDeps) {
    arg.exportedDeps = exportedDeps;
    return this;
  }

  public CxxLibraryBuilder setXcodePrivateHeadersSymlinks(boolean xcodePrivateHeadersSymlinks) {
    arg.xcodePrivateHeadersSymlinks = Optional.of(xcodePrivateHeadersSymlinks);
    return this;
  }

  public CxxLibraryBuilder setXcodePublicHeadersSymlinks(boolean xcodePublicHeadersSymlinks) {
    arg.xcodePublicHeadersSymlinks = Optional.of(xcodePublicHeadersSymlinks);
    return this;
  }

  @Override
  protected CxxLibraryBuilder getThis() {
    return this;
  }

}
