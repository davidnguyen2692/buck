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

package com.facebook.buck.core.rules.type.impl;

import com.facebook.buck.core.rules.type.BuildRuleType;
import com.facebook.buck.util.MoreStrings;
import com.google.common.base.CaseFormat;

public class BuildRuleTypeFactory {

  public static BuildRuleType fromClassName(Class<?> cls) {
    String result = cls.getSimpleName();
    result = MoreStrings.stripPrefix(result, "Abstract").orElse(result);
    result = MoreStrings.stripSuffix(result, "Description").orElse(result);
    return BuildRuleType.of(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, result));
  }
}
