/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.rules.args;

import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolver;
import com.facebook.buck.core.util.immutables.BuckStyleTuple;
import com.google.common.collect.Iterables;
import java.util.Arrays;
import java.util.function.Consumer;
import org.immutables.value.Value;

@Value.Immutable
@BuckStyleTuple
abstract class AbstractStringArg implements Arg {
  @AddToRuleKey
  abstract String getArg();

  @Override
  public void appendToCommandLine(Consumer<String> consumer, SourcePathResolver pathResolver) {
    consumer.accept(getArg());
  }

  public static Iterable<Arg> from(Iterable<String> args) {
    return Iterables.transform(args, StringArg::of);
  }

  public static Iterable<Arg> from(String... args) {
    return from(Arrays.asList(args));
  }
}
