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
package com.facebook.buck.rules.coercer;

import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rulekey.AddsToRuleKey;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import java.util.OptionalInt;
import org.immutables.value.Value;

/**
 * Manifest entries to be injected into the AndroidManifest.xml file via AAPT command line flags.
 */
@JsonDeserialize(as = ManifestEntries.class)
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractManifestEntries implements AddsToRuleKey {
  @Value.Parameter
  @AddToRuleKey
  protected abstract OptionalInt getMinSdkVersion();

  @Value.Parameter
  @AddToRuleKey
  protected abstract OptionalInt getTargetSdkVersion();

  @Value.Parameter
  @AddToRuleKey
  protected abstract OptionalInt getVersionCode();

  @Value.Parameter
  @AddToRuleKey
  protected abstract Optional<String> getVersionName();

  @Value.Parameter
  @AddToRuleKey
  protected abstract Optional<Boolean> getDebugMode();

  @Value.Parameter
  @AddToRuleKey
  protected abstract Optional<ImmutableMap<String, String>> getPlaceholders();

  /** @return true if and only if at least one of the parameters is non-absent. */
  public boolean hasAny() {
    return getMinSdkVersion().isPresent()
        || getTargetSdkVersion().isPresent()
        || getVersionName().isPresent()
        || getVersionCode().isPresent()
        || getDebugMode().isPresent()
        || getPlaceholders().isPresent();
  }

  /** @return an empty (all items set to Optional.empty()) ManifestEntries */
  public static ManifestEntries empty() {
    return ManifestEntries.builder().build();
  }
}
