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
package com.facebook.buck.parser;

import com.facebook.buck.core.model.platform.ConstraintResolver;
import com.facebook.buck.core.model.platform.Platform;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import java.util.function.Supplier;

/**
 * A variant of {@link PerBuildState} that keeps additional information used by {@link
 * ParserWithConfigurableAttributes}.
 */
class PerBuildStateWithConfigurableAttributes extends PerBuildState {

  private final ConstraintResolver constraintResolver;
  private final Supplier<Platform> targetPlatform;

  PerBuildStateWithConfigurableAttributes(
      CellManager cellManager,
      BuildFileRawNodeParsePipeline buildFileRawNodeParsePipeline,
      ParsePipeline<TargetNode<?>> targetNodeParsePipeline,
      ConstraintResolver constraintResolver,
      Supplier<Platform> targetPlatform) {
    super(cellManager, buildFileRawNodeParsePipeline, targetNodeParsePipeline);
    this.constraintResolver = constraintResolver;
    this.targetPlatform = targetPlatform;
  }

  public ConstraintResolver getConstraintResolver() {
    return constraintResolver;
  }

  public Supplier<Platform> getTargetPlatform() {
    return targetPlatform;
  }
}