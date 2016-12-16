/*
 * Copyright 2013-present Facebook, Inc.
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
package com.facebook.buck.event;

import com.facebook.buck.model.BuildId;

/**
 * Thin wrapper around guava event bus.
 *
 * This interface exists only to break circular Buck target dependencies.
 */
public interface EventBus {
  void post(BuckEvent event);

  void post(BuckEvent event, BuckEvent atTime);

  void register(Object object);

  BuildId getBuildId();

  void timestamp(BuckEvent event);
}
