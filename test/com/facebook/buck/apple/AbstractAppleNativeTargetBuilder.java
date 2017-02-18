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

import com.facebook.buck.cxx.CxxSource;
import com.facebook.buck.cxx.NativeLinkable;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourceWithFlags;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;

import java.util.Optional;

public abstract class AbstractAppleNativeTargetBuilder<
    ARG extends AppleNativeTargetDescriptionArg,
    DESCRIPTION extends Description<ARG>,
    BUILDRULE extends BuildRule,
    BUILDER extends AbstractAppleNativeTargetBuilder<ARG, DESCRIPTION, BUILDRULE, BUILDER>>
    extends AbstractNodeBuilder<ARG, DESCRIPTION, BUILDRULE> {

  public AbstractAppleNativeTargetBuilder(
      DESCRIPTION description,
      BuildTarget target) {
    super(description, target);
  }

  public BUILDER setConfigs(ImmutableSortedMap<String, ImmutableMap<String, String>> configs) {
    arg.configs = configs;
    return getThis();
  }

  public BUILDER setCompilerFlags(ImmutableList<String> compilerFlags) {
    arg.compilerFlags = compilerFlags;
    return getThis();
  }

  public BUILDER setPlatformCompilerFlags(
      PatternMatchedCollection<ImmutableList<String>> platformPreprocessorFlags) {
    arg.platformPreprocessorFlags = platformPreprocessorFlags;
    return getThis();
  }

  public BUILDER setPreprocessorFlags(ImmutableList<String> preprocessorFlags) {
    arg.preprocessorFlags = preprocessorFlags;
    return getThis();
  }

  public BUILDER setLangPreprocessorFlags(
      ImmutableMap<CxxSource.Type, ImmutableList<String>> langPreprocessorFlags) {
    arg.langPreprocessorFlags = langPreprocessorFlags;
    return getThis();
  }

  public BUILDER setExportedPreprocessorFlags(
      ImmutableList<String> exportedPreprocessorFlags) {
    arg.exportedPreprocessorFlags = exportedPreprocessorFlags;
    return getThis();
  }

  public BUILDER setLinkerFlags(ImmutableList<StringWithMacros> linkerFlags) {
    arg.linkerFlags = linkerFlags;
    return getThis();
  }

  public BUILDER setExportedLinkerFlags(ImmutableList<StringWithMacros> exportedLinkerFlags) {
    arg.exportedLinkerFlags = exportedLinkerFlags;
    return getThis();
  }

  public BUILDER setSrcs(ImmutableSortedSet<SourceWithFlags> srcs) {
    arg.srcs = srcs;
    return getThis();
  }

  public BUILDER setExtraXcodeSources(ImmutableList<SourcePath> extraXcodeSources) {
    arg.extraXcodeSources = extraXcodeSources;
    return getThis();
  }

  public BUILDER setHeaders(SourceList headers) {
    arg.headers = headers;
    return getThis();
  }

  public BUILDER setHeaders(ImmutableSortedSet<SourcePath> headers) {
    return setHeaders(SourceList.ofUnnamedSources(headers));
  }

  public BUILDER setHeaders(ImmutableSortedMap<String, SourcePath> headers) {
    return setHeaders(SourceList.ofNamedSources(headers));
  }

  public BUILDER setExportedHeaders(SourceList exportedHeaders) {
    arg.exportedHeaders = exportedHeaders;
    return getThis();
  }

  public BUILDER setExportedHeaders(ImmutableSortedSet<SourcePath> exportedHeaders) {
    return setExportedHeaders(SourceList.ofUnnamedSources(exportedHeaders));
  }

  public BUILDER setExportedHeaders(ImmutableSortedMap<String, SourcePath> exportedHeaders) {
    return setExportedHeaders(SourceList.ofNamedSources(exportedHeaders));
  }

  public BUILDER setFrameworks(ImmutableSortedSet<FrameworkPath> frameworks) {
    arg.frameworks = frameworks;
    return getThis();
  }

  public BUILDER setLibraries(ImmutableSortedSet<FrameworkPath> libraries) {
    arg.libraries = libraries;
    return getThis();
  }

  public BUILDER setDeps(ImmutableSortedSet<BuildTarget> deps) {
    arg.deps = deps;
    return getThis();
  }

  public BUILDER setExportedDeps(ImmutableSortedSet<BuildTarget> exportedDeps) {
    arg.exportedDeps = exportedDeps;
    return getThis();
  }

  public BUILDER setHeaderPathPrefix(Optional<String> headerPathPrefix) {
    arg.headerPathPrefix = headerPathPrefix;
    return getThis();
  }

  public BUILDER setPrefixHeader(Optional<SourcePath> prefixHeader) {
    arg.prefixHeader = prefixHeader;
    return getThis();
  }

  public BUILDER setTests(ImmutableSortedSet<BuildTarget> tests) {
    arg.tests = tests;
    return getThis();
  }

  public BUILDER setBridgingHeader(Optional<SourcePath> bridgingHeader) {
    arg.bridgingHeader = bridgingHeader;
    return getThis();
  }

  public BUILDER setPreferredLinkage(NativeLinkable.Linkage linkage) {
    arg.preferredLinkage = Optional.of(linkage);
    return getThis();
  }

  protected abstract BUILDER getThis();
}
