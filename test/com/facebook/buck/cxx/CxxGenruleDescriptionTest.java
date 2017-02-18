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
package com.facebook.buck.cxx;

import static org.junit.Assert.assertThat;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetGraphAndBuildTargets;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.macros.StringWithMacrosUtils;
import com.facebook.buck.shell.Genrule;
import com.facebook.buck.testutil.OptionalMatchers;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.facebook.buck.versions.FixedTargetNodeTranslator;
import com.facebook.buck.versions.NaiveVersionSelector;
import com.facebook.buck.versions.TargetNodeTranslator;
import com.facebook.buck.versions.VersionPropagatorBuilder;
import com.facebook.buck.versions.VersionedAliasBuilder;
import com.facebook.buck.versions.VersionedTargetGraphBuilder;
import com.facebook.buck.versions.VersionedTargetGraphFactory;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;

public class CxxGenruleDescriptionTest {

  private static final ForkJoinPool POOL = new ForkJoinPool(1);

  @Test
  public void toolPlatformParseTimeDeps() {
    for (String macro : ImmutableSet.of("ld", "cc", "cxx")) {
      CxxGenruleBuilder builder =
          new CxxGenruleBuilder(BuildTargetFactory.newInstance("//:rule#default"))
              .setCmd(String.format("$(%s)", macro));
      assertThat(
          ImmutableSet.copyOf(builder.findImplicitDeps()),
          Matchers.equalTo(
              ImmutableSet.copyOf(
                  CxxPlatforms.getParseTimeDeps(CxxPlatformUtils.DEFAULT_PLATFORM))));
    }
  }

  @Test
  public void ldFlagsFilter() throws Exception {
    for (Linker.LinkableDepType style : Linker.LinkableDepType.values()) {
      CxxLibraryBuilder bBuilder =
          new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:b"))
              .setExportedLinkerFlags(
                  ImmutableList.of(StringWithMacrosUtils.format("-b")));
      CxxLibraryBuilder aBuilder =
          new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:a"))
              .setExportedDeps(ImmutableSortedSet.of(bBuilder.getTarget()))
              .setExportedLinkerFlags(
                  ImmutableList.of(StringWithMacrosUtils.format("-a")));
      CxxGenruleBuilder builder =
          new CxxGenruleBuilder(
              BuildTargetFactory.newInstance(
                  "//:rule#" + CxxPlatformUtils.DEFAULT_PLATFORM.getFlavor()))
              .setOut("out")
              .setCmd(
                  String.format(
                      "$(ldflags-%s-filter //:a //:a)",
                      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, style.toString())));
      TargetGraph targetGraph =
          TargetGraphFactory.newInstance(
              bBuilder.build(),
              aBuilder.build(),
              builder.build());
      BuildRuleResolver resolver =
          new BuildRuleResolver(
              targetGraph,
              new DefaultTargetNodeToBuildRuleTransformer());
      bBuilder.build(resolver);
      aBuilder.build(resolver);
      Genrule genrule = (Genrule) builder.build(resolver);
      assertThat(
          Joiner.on(' ').join(Arg.stringify(ImmutableList.of(genrule.getCmd().get()))),
          Matchers.containsString("-a"));
      assertThat(
          Joiner.on(' ').join(Arg.stringify(ImmutableList.of(genrule.getCmd().get()))),
          Matchers.not(Matchers.containsString("-b")));
    }
  }

  @Test
  public void cppflagsNoArgs() throws Exception {
    CxxPlatform cxxPlatform =
        CxxPlatformUtils.DEFAULT_PLATFORM
            .withCppflags("-cppflag")
            .withCxxppflags("-cxxppflag");
    CxxGenruleBuilder builder =
        new CxxGenruleBuilder(
            BuildTargetFactory.newInstance("//:rule#" + cxxPlatform.getFlavor()),
            new FlavorDomain<>(
                "C/C++ Platform",
                ImmutableMap.of(cxxPlatform.getFlavor(), cxxPlatform)))
            .setOut("out")
            .setCmd("$(cppflags) $(cxxppflags)");
    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            builder.build());
    BuildRuleResolver resolver =
        new BuildRuleResolver(
            targetGraph,
            new DefaultTargetNodeToBuildRuleTransformer());
    Genrule genrule = (Genrule) builder.build(resolver);
    assertThat(
        Joiner.on(' ').join(Arg.stringify(ImmutableList.of(genrule.getCmd().get()))),
        Matchers.containsString("-cppflag -cxxppflag"));
  }

  @Test
  public void cflagsNoArgs() throws Exception {
    CxxPlatform cxxPlatform =
        CxxPlatformUtils.DEFAULT_PLATFORM
            .withAsflags("-asflag")
            .withCflags("-cflag")
            .withCxxflags("-cxxflag");
    CxxGenruleBuilder builder =
        new CxxGenruleBuilder(
            BuildTargetFactory.newInstance("//:rule#" + cxxPlatform.getFlavor()),
            new FlavorDomain<>(
                "C/C++ Platform",
                ImmutableMap.of(cxxPlatform.getFlavor(), cxxPlatform)))
            .setOut("out")
            .setCmd("$(cflags) $(cxxflags)");
    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            builder.build());
    BuildRuleResolver resolver =
        new BuildRuleResolver(
            targetGraph,
            new DefaultTargetNodeToBuildRuleTransformer());
    Genrule genrule = (Genrule) builder.build(resolver);
    for (String expected : ImmutableList.of("-asflag", "-cflag", "-cxxflag")) {
      assertThat(
          Joiner.on(' ').join(Arg.stringify(ImmutableList.of(genrule.getCmd().get()))),
          Matchers.containsString(expected));
    }
  }

  @Test
  public void targetTranslateConstructorArg() throws NoSuchBuildTargetException {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:lib");
    BuildTarget original = BuildTargetFactory.newInstance("//hello:world");
    BuildTarget translated = BuildTargetFactory.newInstance("//something:else");
    CxxGenruleBuilder builder =
        new CxxGenruleBuilder(target)
            .setCmd(String.format("$(cppflags %s)", original));
    TargetNode<CxxGenruleDescription.Arg, CxxGenruleDescription> node = builder.build();
    TargetNodeTranslator translator =
        new FixedTargetNodeTranslator(ImmutableMap.of(original, translated));
    Optional<CxxGenruleDescription.Arg> translatedArg =
        node.getDescription().translateConstructorArg(
            target,
            node.getCellNames(),
            translator,
            node.getConstructorArg());
    assertThat(
        translatedArg.get().cmd.get(),
        Matchers.equalTo("$(cppflags //something:else)"));
  }

  @Test
  public void versionedTargetReferenceIsTranslatedInVersionedGraph() throws Exception {
    VersionPropagatorBuilder dep =
        new VersionPropagatorBuilder("//:dep");
    VersionedAliasBuilder versionedDep =
        new VersionedAliasBuilder("//:versioned")
            .setVersions("1.0", "//:dep");
    CxxGenruleBuilder genruleBuilder =
        new CxxGenruleBuilder(BuildTargetFactory.newInstance("//:genrule"))
            .setCmd("$(ldflags-shared //:versioned)");
    TargetGraph graph =
        VersionedTargetGraphFactory.newInstance(
            dep.build(),
            versionedDep.build(),
            genruleBuilder.build());
    TargetGraphAndBuildTargets transformed =
        VersionedTargetGraphBuilder.transform(
            new NaiveVersionSelector(),
            TargetGraphAndBuildTargets.of(graph, ImmutableSet.of(genruleBuilder.getTarget())),
            POOL);
    CxxGenruleDescription.Arg arg =
        extractArg(
            transformed.getTargetGraph().get(genruleBuilder.getTarget()),
            CxxGenruleDescription.Arg.class);
    assertThat(
        arg.cmd,
        OptionalMatchers.present(Matchers.equalTo("$(ldflags-shared //:dep)")));
  }

  @Test
  public void versionPropagatorTargetReferenceIsTranslatedInVersionedGraph() throws Exception {
    VersionPropagatorBuilder transitiveDep =
        new VersionPropagatorBuilder("//:transitive_dep");
    VersionedAliasBuilder versionedDep =
        new VersionedAliasBuilder("//:versioned")
            .setVersions("1.0", "//:transitive_dep");
    VersionPropagatorBuilder dep =
        new VersionPropagatorBuilder("//:dep")
            .setDeps("//:versioned");
    CxxGenruleBuilder genruleBuilder =
        new CxxGenruleBuilder(BuildTargetFactory.newInstance("//:genrule"))
            .setCmd("$(ldflags-shared //:dep)");
    TargetGraph graph =
        VersionedTargetGraphFactory.newInstance(
            transitiveDep.build(),
            versionedDep.build(),
            dep.build(),
            genruleBuilder.build());
    TargetGraphAndBuildTargets transformed =
        VersionedTargetGraphBuilder.transform(
            new NaiveVersionSelector(),
            TargetGraphAndBuildTargets.of(graph, ImmutableSet.of(genruleBuilder.getTarget())),
            POOL);
    CxxGenruleDescription.Arg arg =
        extractArg(
            transformed.getTargetGraph().get(genruleBuilder.getTarget()),
            CxxGenruleDescription.Arg.class);
    assertThat(
        arg.cmd,
        OptionalMatchers.present(
            Matchers.matchesPattern(
                Pattern.quote(
                    "$(ldflags-shared //:dep#v") + "[a-zA-Z0-9]*" + Pattern.quote(")"))));
  }

  private static <U> U extractArg(TargetNode<?, ?> node, Class<U> clazz) {
    return node.castArg(clazz)
        .orElseThrow(
            () -> new AssertionError(
                String.format(
                    "%s: expected constructor arg to be of type %s (was %s)",
                    node,
                    clazz,
                    node.getConstructorArg().getClass())))
        .getConstructorArg();
  }

}
