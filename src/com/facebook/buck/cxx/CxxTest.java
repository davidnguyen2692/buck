/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.cxx;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BinaryBuildRule;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.Label;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TestRule;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.TouchStep;
import com.facebook.buck.test.TestCaseSummary;
import com.facebook.buck.test.TestResultSummary;
import com.facebook.buck.test.TestResults;
import com.facebook.buck.test.TestRunningOptions;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * A no-op {@link BuildRule} which houses the logic to run and form the results for C/C++ tests.
 */
public abstract class CxxTest
    extends AbstractBuildRule
    implements TestRule, HasRuntimeDeps, BinaryBuildRule {

  @AddToRuleKey
  private final Supplier<ImmutableMap<String, String>> env;
  @AddToRuleKey
  private final Supplier<ImmutableList<String>> args;
  @AddToRuleKey
  @SuppressWarnings("PMD.UnusedPrivateField")
  private final ImmutableSortedSet<SourcePath> resources;
  private final Supplier<ImmutableSortedSet<BuildRule>> additionalDeps;
  private final ImmutableSet<Label> labels;
  private final ImmutableSet<String> contacts;
  private final boolean runTestSeparately;
  private final Optional<Long> testRuleTimeoutMs;

  public CxxTest(
      BuildRuleParams params,
      final ImmutableMap<String, String> toolEnv,
      final Supplier<ImmutableMap<String, String>> env,
      Supplier<ImmutableList<String>> args,
      ImmutableSortedSet<SourcePath> resources,
      Supplier<ImmutableSortedSet<BuildRule>> additionalDeps,
      ImmutableSet<Label> labels,
      ImmutableSet<String> contacts,
      boolean runTestSeparately,
      Optional<Long> testRuleTimeoutMs) {
    super(params);
    this.env = Suppliers.memoize(
        () -> {
          ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
          builder.putAll(toolEnv);
          builder.putAll(env.get());
          return builder.build();
        });
    this.args = Suppliers.memoize(args);
    this.resources = resources;
    this.additionalDeps = Suppliers.memoize(additionalDeps);
    this.labels = labels;
    this.contacts = contacts;
    this.runTestSeparately = runTestSeparately;
    this.testRuleTimeoutMs = testRuleTimeoutMs;
  }

  @Override
  public final ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {
    return ImmutableList.of();
  }

  /**
   * @return the path to which the test commands output is written.
   */
  @VisibleForTesting
  protected Path getPathToTestExitCode() {
    return getPathToTestOutputDirectory().resolve("exitCode");
  }

  /**
   * @return the path to which the test commands output is written.
   */
  @VisibleForTesting
  protected Path getPathToTestOutput() {
    return getPathToTestOutputDirectory().resolve("output");
  }

  /**
   * @return the path to which the framework-specific test results are written.
   */
  @VisibleForTesting
  protected Path getPathToTestResults() {
    return getPathToTestOutputDirectory().resolve("results");
  }

  /**
   * @return the shell command used to run the test.
   */
  protected abstract ImmutableList<String> getShellCommand(
      SourcePathResolver pathResolver,
      Path output);

  @Override
  public boolean hasTestResultFiles() {
    return getProjectFilesystem().isFile(getPathToTestResults());
  }

  @Override
  public ImmutableList<Step> runTests(
      ExecutionContext executionContext,
      TestRunningOptions options,
      SourcePathResolver pathResolver,
      TestReportingCallback testReportingCallback) {
    return ImmutableList.of(
        new MakeCleanDirectoryStep(getProjectFilesystem(), getPathToTestOutputDirectory()),
        new TouchStep(getProjectFilesystem(), getPathToTestResults()),
        new CxxTestStep(
            getProjectFilesystem(),
            ImmutableList.<String>builder()
                .addAll(getShellCommand(pathResolver, getPathToTestResults()))
                .addAll(args.get())
                .build(),
            env.get(),
            getPathToTestExitCode(),
            getPathToTestOutput(),
            testRuleTimeoutMs));
  }

  protected abstract ImmutableList<TestResultSummary> parseResults(
      Path exitCode,
      Path output,
      Path results)
      throws Exception;

  @Override
  public Callable<TestResults> interpretTestResults(
      final ExecutionContext executionContext,
      boolean isUsingTestSelectors) {
    return () -> {
      return TestResults.of(
          getBuildTarget(),
          ImmutableList.of(
              new TestCaseSummary(
                  getBuildTarget().getFullyQualifiedName(),
                  parseResults(
                      getPathToTestExitCode(),
                      getPathToTestOutput(),
                      getPathToTestResults()))),
          contacts,
          labels.stream()
              .map(Object::toString)
              .collect(MoreCollectors.toImmutableSet()));
    };
  }

  @Override
  public ImmutableSet<Label> getLabels() {
    return labels;
  }

  @Override
  public ImmutableSet<String> getContacts() {
    return contacts;
  }

  @Override
  public Path getPathToTestOutputDirectory() {
    return BuildTargets.getGenPath(
        getProjectFilesystem(),
        getBuildTarget(),
        "__test_%s_output__");
  }

  @Override
  public boolean runTestSeparately() {
    return runTestSeparately;
  }

  @Override
  public boolean supportsStreamingTests() {
    return false;
  }

  @Override
  public Stream<BuildTarget> getRuntimeDeps() {
    return additionalDeps.get().stream().map(BuildRule::getBuildTarget);
  }

  protected Supplier<ImmutableMap<String, String>> getEnv() {
    return env;
  }

  protected Supplier<ImmutableList<String>> getArgs() {
    return args;
  }

}
