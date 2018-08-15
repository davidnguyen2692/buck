/*
 * Copyright 2018-present Facebook, Inc.
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

package com.facebook.buck.core.rules.config.impl;

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rules.config.ConfigurationRule;
import com.facebook.buck.core.rules.config.ConfigurationRuleResolver;
import com.facebook.buck.core.select.ProvidesSelectable;
import com.facebook.buck.core.select.Selectable;
import com.facebook.buck.core.select.SelectableResolver;
import com.google.common.base.Preconditions;

/**
 * Resolves {@link Selectable} by querying {@link ConfigurationRule}.
 *
 * <p>{@link ConfigurationRule} needs to implement {@link ProvidesSelectable} in order to be used by
 * this resolver.
 */
public class ConfigurationRuleSelectableResolver implements SelectableResolver {

  private final ConfigurationRuleResolver configurationRuleResolver;

  public ConfigurationRuleSelectableResolver(ConfigurationRuleResolver configurationRuleResolver) {
    this.configurationRuleResolver = configurationRuleResolver;
  }

  @Override
  public Selectable getSelectable(BuildTarget buildTarget) {
    ConfigurationRule configurationRule = configurationRuleResolver.getRule(buildTarget);
    Preconditions.checkArgument(configurationRule instanceof ProvidesSelectable);
    return ((ProvidesSelectable) configurationRule).getSelectable();
  }
}
