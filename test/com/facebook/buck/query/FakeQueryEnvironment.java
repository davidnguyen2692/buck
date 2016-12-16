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

package com.facebook.buck.query;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Set;

/** Utility class used to test the QueryParser */
public class FakeQueryEnvironment implements QueryEnvironment {

  public FakeQueryEnvironment() {}

  // This is the only method needed for tests.
  @Override
  public Iterable<QueryFunction> getFunctions() {
    return DEFAULT_QUERY_FUNCTIONS;
  }

  @Override
  public Set<QueryTarget> getTargetsMatchingPattern(
      String pattern,
      ListeningExecutorService executor)
      throws QueryException, InterruptedException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

  @Override
  public Set<QueryTarget> getFwdDeps(Iterable<QueryTarget> targets)
      throws QueryException, InterruptedException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

  @Override
  public Set<QueryTarget> getReverseDeps(Iterable<QueryTarget> targets)
      throws QueryException, InterruptedException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

  @Override
  public Set<QueryTarget> getInputs(QueryTarget targets)
      throws QueryException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

  @Override
  public Set<QueryTarget> getTransitiveClosure(Set<QueryTarget> targets)
      throws QueryException, InterruptedException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

  @Override
  public void buildTransitiveClosure(
      Set<QueryTarget> targetNodes,
      int maxDepth,
      ListeningExecutorService executor)
      throws InterruptedException, QueryException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

  @Override
  public String getTargetKind(QueryTarget target)
      throws InterruptedException, QueryException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

  @Override
  public ImmutableSet<QueryTarget> getTestsForTarget(QueryTarget target)
      throws InterruptedException, QueryException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

  @Override
  public ImmutableSet<QueryTarget> getBuildFiles(Set<QueryTarget> targets)
      throws QueryException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

  @Override
  public ImmutableSet<QueryTarget> getFileOwners(
      ImmutableList<String> files,
      ListeningExecutorService executor)
      throws InterruptedException, QueryException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

  @Override
  public ImmutableSet<QueryTarget> getTargetsInAttribute(QueryTarget target, String attribute)
      throws InterruptedException, QueryException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

  @Override
  public ImmutableSet<Object> filterAttributeContents(
      QueryTarget target,
      String attribute,
      Predicate<Object> predicate)
      throws InterruptedException, QueryException {
    throw new QueryException("Method not implemented in FakeQueryEnvironment");
  }

}
