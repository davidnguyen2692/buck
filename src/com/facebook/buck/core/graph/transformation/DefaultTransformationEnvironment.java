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

package com.facebook.buck.core.graph.transformation;

import com.facebook.buck.core.graph.transformation.compute.ComputeKey;
import com.facebook.buck.core.graph.transformation.compute.ComputeResult;
import com.google.common.collect.ImmutableMap;
import java.util.Map.Entry;

/**
 * A computation environment that {@link GraphTransformer} can access. This class provides ability
 * of {@link GraphTransformer}s to access their dependencies.
 */
class DefaultTransformationEnvironment implements TransformationEnvironment {

  private final ImmutableMap<? extends ComputeKey<?>, ? extends ComputeResult> deps;

  /**
   * Package protected constructor so only {@link DefaultGraphTransformationEngine} can create the
   * environment
   */
  DefaultTransformationEnvironment(
      ImmutableMap<? extends ComputeKey<?>, ? extends ComputeResult> deps) {
    this.deps = deps;
  }

  @Override
  public ImmutableMap<? extends ComputeKey<?>, ? extends ComputeResult> getDeps() {
    return deps;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <KeyType extends ComputeKey<ResultType>, ResultType extends ComputeResult>
      ResultType getDep(KeyType key) {
    return (ResultType) deps.get(key);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <KeyType extends ComputeKey<ResultType>, ResultType extends ComputeResult>
      ImmutableMap<KeyType, ResultType> getDeps(Class<KeyType> keyClass) {
    ImmutableMap.Builder<KeyType, ResultType> resultBuilder =
        ImmutableMap.builderWithExpectedSize(deps.size());

    for (Entry<? extends ComputeKey<?>, ? extends ComputeResult> dep : deps.entrySet()) {
      if (dep.getKey().getKeyClass().equals(keyClass)) {
        resultBuilder.put((KeyType) dep.getKey(), (ResultType) dep.getValue());
      }
    }
    return resultBuilder.build();
  }
}
