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

import com.google.common.collect.ImmutableMap;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class TestTransformationEnvironment<Key, Result>
    implements TransformationEnvironment<Key, Result> {

  @Override
  public CompletionStage<Result> evaluate(Key key, Function<Result, Result> asyncTransformation) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletionStage<Result> evaluateAll(
      Iterable<Key> keys, Function<ImmutableMap<Key, Result>, Result> asyncTransformation) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletionStage<Result> evaluateAllAndCollectAsync(
      Iterable<Key> keys, TransformationEnvironment.AsyncSink<Key, Result> sink) {
    throw new UnsupportedOperationException();
  }
}
