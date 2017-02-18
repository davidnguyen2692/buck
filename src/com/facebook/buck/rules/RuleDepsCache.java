/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.rules;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.ExecutionException;

/**
 * A "loading cache" of rule deps futures.
 *
 * Not a mere LoadingCache since the key is a build target but the loader uses a build rule.
 */
public class RuleDepsCache {
  private final ListeningExecutorService service;
  private final BuildRuleResolver resolver;
  private final Cache<BuildTarget, ListenableFuture<ImmutableSortedSet<BuildRule>>> cache;

  public RuleDepsCache(ListeningExecutorService service, BuildRuleResolver resolver) {
    this.service = service;
    this.resolver = resolver;
    this.cache = CacheBuilder.newBuilder().build();
  }

  public ListenableFuture<ImmutableSortedSet<BuildRule>> get(final BuildRule rule) {
    try {
      return cache.get(
          rule.getBuildTarget(),
          () -> service.submit(() -> {
            ImmutableSortedSet.Builder<BuildRule> deps = ImmutableSortedSet.naturalOrder();
            deps.addAll(rule.getDeps());
            if (rule instanceof HasRuntimeDeps) {
              deps.addAll(
                  resolver.getAllRules(((HasRuntimeDeps) rule).getRuntimeDeps()
                      .collect(MoreCollectors.toImmutableSet())));
            }
            return deps.build();
          }));
    } catch (ExecutionException e) {
      // service.submit doesn't throw any checked exceptions, so this should be fine.
      Throwables.throwIfUnchecked(e.getCause());
      throw new RuntimeException(e.getCause());
    }
  }
}
