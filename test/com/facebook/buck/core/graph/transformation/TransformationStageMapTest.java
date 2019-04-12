/*
 * Copyright 2019-present Facebook, Inc.
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

import static org.junit.Assert.assertEquals;

import com.facebook.buck.core.graph.transformation.ChildrenAdder.LongNode;
import com.facebook.buck.core.graph.transformation.compute.ComputeKey;
import com.facebook.buck.core.graph.transformation.compute.ComputeResult;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TransformationStageMapTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void createAndGetWithSingleStage() {
    GraphTransformationStage<?, ?> stage = new FakeTransformationStage<>(LongNode.class);
    TransformationStageMap map = TransformationStageMap.from(ImmutableList.of(stage));

    assertEquals(stage, map.get(ImmutableLongNode.of(1)));
  }

  @Test
  public void createAndGetWithMultipleStage() {
    GraphTransformationStage<?, ?> stage1 = new FakeTransformationStage<>(LongNode.class);
    GraphTransformationStage<?, ?> stage2 = new FakeTransformationStage<>(MyLongNode.class);
    TransformationStageMap map = TransformationStageMap.from(ImmutableList.of(stage1, stage2));

    assertEquals(stage1, map.get(ImmutableLongNode.of(1)));
    assertEquals(stage2, map.get(new MyLongNode()));
  }

  @Test
  public void unknownKeyThrowsException() {
    expectedException.expect(VerifyException.class);
    TransformationStageMap map = TransformationStageMap.from(ImmutableList.of());
    map.get(new MyLongNode());
  }

  static class MyLongNode implements ComputeKey<MyLongNode>, ComputeResult {

    @Override
    public Class<MyLongNode> getKeyClass() {
      return MyLongNode.class;
    }
  }

  static class FakeTransformationStage<Key extends ComputeKey<Result>, Result extends ComputeResult>
      extends GraphTransformationStage<Key, Result> {

    public FakeTransformationStage(Class<Key> keyClass) {
      super(
          new GraphTransformer<Key, Result>() {
            @Override
            public Class<Key> getKeyClass() {
              return keyClass;
            }

            @Override
            public Result transform(Key key, TransformationEnvironment env) {
              throw new UnsupportedOperationException();
            }

            @Override
            public ImmutableSet<Key> discoverDeps(Key key, TransformationEnvironment env) {
              return ImmutableSet.of();
            }

            @Override
            public ImmutableSet<Key> discoverPreliminaryDeps(Key key) {
              return ImmutableSet.of();
            }
          },
          new GraphEngineCache<Key, Result>() {
            @Override
            public Optional<Result> get(Key key) {
              return Optional.empty();
            }

            @Override
            public void put(Key key, Result result) {}
          });
    }
  }
}
