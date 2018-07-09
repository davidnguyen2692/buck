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

package com.facebook.buck.versions;

import com.facebook.buck.core.description.BuildRuleParams;
import com.facebook.buck.core.description.arg.CommonDescriptionArg;
import com.facebook.buck.core.description.arg.HasDeclaredDeps;
import com.facebook.buck.core.description.arg.HasTests;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.targetgraph.AbstractNodeBuilder;
import com.facebook.buck.core.model.targetgraph.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.facebook.buck.model.BuildTargetFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

public class VersionPropagatorBuilder
    extends AbstractNodeBuilder<
        VersionPropagatorDescriptionArg.Builder,
        VersionPropagatorDescriptionArg,
        VersionPropagatorBuilder.VersionPropagatorDescription,
        BuildRule> {

  public VersionPropagatorBuilder(BuildTarget target) {
    super(new VersionPropagatorDescription(), target);
  }

  public VersionPropagatorBuilder(String target) {
    this(BuildTargetFactory.newInstance(target));
  }

  public VersionPropagatorBuilder setDeps(ImmutableSortedSet<BuildTarget> deps) {
    getArgForPopulating().setDeps(deps);
    return this;
  }

  public VersionPropagatorBuilder setDeps(String... deps) {
    ImmutableSortedSet.Builder<BuildTarget> builder = ImmutableSortedSet.naturalOrder();
    for (String dep : deps) {
      builder.add(BuildTargetFactory.newInstance(dep));
    }
    return setDeps(builder.build());
  }

  public VersionPropagatorBuilder setVersionedDeps(
      ImmutableSortedMap<BuildTarget, Optional<Constraint>> deps) {
    getArgForPopulating().setVersionedDeps(deps);
    return this;
  }

  @SafeVarargs
  public final VersionPropagatorBuilder setVersionedDeps(
      Map.Entry<BuildTarget, Optional<Constraint>>... deps) {
    return setVersionedDeps(ImmutableSortedMap.copyOf(ImmutableList.copyOf(deps)));
  }

  public VersionPropagatorBuilder setVersionedDeps(String target, Constraint constraint) {
    return setVersionedDeps(
        new AbstractMap.SimpleEntry<>(
            BuildTargetFactory.newInstance(target), Optional.of(constraint)));
  }

  public VersionPropagatorBuilder setTests(ImmutableSortedSet<BuildTarget> tests) {
    getArgForPopulating().setTests(tests);
    return this;
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractVersionPropagatorDescriptionArg
      extends CommonDescriptionArg, HasDeclaredDeps, HasTests {
    @Value.NaturalOrder
    ImmutableSortedMap<BuildTarget, Optional<Constraint>> getVersionedDeps();
  }

  public static class VersionPropagatorDescription
      implements VersionPropagator<VersionPropagatorDescriptionArg> {

    @Override
    public Class<VersionPropagatorDescriptionArg> getConstructorArgType() {
      return VersionPropagatorDescriptionArg.class;
    }

    @Override
    public BuildRule createBuildRule(
        BuildRuleCreationContextWithTargetGraph context,
        BuildTarget buildTarget,
        BuildRuleParams params,
        VersionPropagatorDescriptionArg args) {
      throw new IllegalStateException();
    }
  }
}
