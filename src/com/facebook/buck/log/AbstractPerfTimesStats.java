/*
 * Copyright 2017-present Facebook, Inc.
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

package com.facebook.buck.log;

import com.facebook.buck.log.views.JsonViews;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.fasterxml.jackson.annotation.JsonView;

import org.immutables.value.Value;

@Value.Immutable
@BuckStyleImmutable
abstract class AbstractPerfTimesStats {

  @JsonView(JsonViews.MachineReadableLog.class)
  @Value.Default
  public Long getPythonTimeMs() {
    return 0L;
  }

  @JsonView(JsonViews.MachineReadableLog.class)
  @Value.Default
  public Long getInitTimeMs() {
    return 0L;
  }

  @JsonView(JsonViews.MachineReadableLog.class)
  @Value.Default
  public Long getParseTimeMs() {
    return 0L;
  }

  @JsonView(JsonViews.MachineReadableLog.class)
  @Value.Default
  public Long getProcessingTimeMs() {
    return 0L;
  }

  @JsonView(JsonViews.MachineReadableLog.class)
  @Value.Default
  public Long getActionGraphTimeMs() {
    return 0L;
  }

  @JsonView(JsonViews.MachineReadableLog.class)
  @Value.Default
  public Long getRulekeyTimeMs() {
    return 0L;
  }

  @JsonView(JsonViews.MachineReadableLog.class)
  @Value.Default
  public Long getFetchTimeMs() {
    return 0L;
  }

  @JsonView(JsonViews.MachineReadableLog.class)
  @Value.Default
  public Long getBuildTimeMs() {
    return 0L;
  }

  @JsonView(JsonViews.MachineReadableLog.class)
  @Value.Default
  public Long getInstallTimeMs() {
    return 0L;
  }

}
