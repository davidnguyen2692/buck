/*
 * Copyright 2012-present Facebook, Inc.
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

package com.facebook.buck.cli;

import com.facebook.buck.command.Build;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.json.BuildFileParseException;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetException;
import com.facebook.buck.parser.BuildFileSpec;
import com.facebook.buck.parser.ParserConfig;
import com.facebook.buck.parser.TargetNodePredicateSpec;
import com.facebook.buck.rules.ActionGraphAndResolver;
import com.facebook.buck.rules.BuildEngine;
import com.facebook.buck.rules.BuildEvent;
import com.facebook.buck.rules.CachingBuildEngine;
import com.facebook.buck.rules.CachingBuildEngineBuckConfig;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.ExternalTestRunnerRule;
import com.facebook.buck.rules.ExternalTestRunnerTestSpec;
import com.facebook.buck.rules.Label;
import com.facebook.buck.rules.LocalCachingBuildEngineDelegate;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetGraphAndBuildTargets;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.TargetNodes;
import com.facebook.buck.rules.TestRule;
import com.facebook.buck.rules.keys.RuleKeyFactoryManager;
import com.facebook.buck.step.AdbOptions;
import com.facebook.buck.step.DefaultStepRunner;
import com.facebook.buck.step.TargetDevice;
import com.facebook.buck.step.TargetDeviceOptions;
import com.facebook.buck.test.CoverageReportFormat;
import com.facebook.buck.test.TestRunningOptions;
import com.facebook.buck.util.ForwardingProcessListener;
import com.facebook.buck.util.ListeningProcessExecutor;
import com.facebook.buck.util.MoreCollectors;
import com.facebook.buck.util.MoreExceptions;
import com.facebook.buck.util.ProcessExecutorParams;
import com.facebook.buck.util.RichStream;
import com.facebook.buck.util.concurrent.ConcurrencyLimit;
import com.facebook.buck.versions.VersionException;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

public class TestCommand extends BuildCommand {

  public static final String USE_RESULTS_CACHE = "use_results_cache";
  public static final String RERUN_ONLY_FAILING = "rerun_only_failing";

  private static final Logger LOG = Logger.get(TestCommand.class);

  @Option(name = "--all",
          usage =
              "Whether all of the tests should be run. " +
              "If no targets are given, --all is implied")
  private boolean all = false;

  @Option(name = "--code-coverage", usage = "Whether code coverage information will be generated.")
  private boolean isCodeCoverageEnabled = false;

  @Option(name = "--code-coverage-format", usage = "Format to be used for coverage")
  private CoverageReportFormat coverageReportFormat = CoverageReportFormat.HTML;

  @Option(name = "--code-coverage-title", usage = "Title used for coverage")
  private String coverageReportTitle = "Code-Coverage Analysis";

  @Option(name = "--debug",
          usage = "Whether the test will start suspended with a JDWP debug port of 5005")
  private boolean isDebugEnabled = false;

  @Option(name = "--xml", usage = "Where to write test output as XML.")
  @Nullable
  private String pathToXmlTestOutput = null;

  @Option(name = "--run-with-java-agent",
      usage = "Whether the test will start a java profiling agent")
  @Nullable
  private String pathToJavaAgent = null;

  @Option(name = "--no-results-cache", usage = "Whether to use cached test results.")
  @Nullable
  private Boolean isResultsCacheDisabled = null;

  @Option(name = "--only-failing", usage = "Don't re-run tests that are cached as passed.")
  @Nullable
  private Boolean isOnlyFailing = null;

  @Option(name = "--build-filtered", usage = "Whether to build filtered out tests.")
  @Nullable
  private Boolean isBuildFiltered = null;

  // TODO(#9061229): See if we can remove this option entirely. For now, the
  // underlying code has been removed, and this option is ignored.
  @Option(
      name = "--ignore-when-dependencies-fail",
      aliases = {"-i"},
      usage =
          "Deprecated option (ignored).",
      hidden = true)
  @SuppressWarnings("PMD.UnusedPrivateField")
  private boolean isIgnoreFailingDependencies;

  @Option(
      name = "--shuffle",
      usage =
          "Randomize the order in which test classes are executed." +
          "WARNING: only works for Java tests!")
  private boolean isShufflingTests;

  @Option(
      name = "--exclude-transitive-tests",
      usage =
          "Only run the tests targets that were specified on the command line (without adding " +
          "more tests by following dependencies).")
  private boolean shouldExcludeTransitiveTests;

  @Option(
      name = "--test-runner-env",
      usage =
          "Add or override an environment variable passed to the test runner. Later occurrences " +
          "override earlier occurrences. Currently this only support Apple(ios/osx) tests.",
      handler = EnvironmentOverrideOptionHandler.class)
  private Map<String, String> environmentOverrides = new HashMap<>();

  @AdditionalOptions
  @SuppressFieldNotInitialized
  private AdbCommandLineOptions adbOptions;

  @AdditionalOptions
  @SuppressFieldNotInitialized
  private TargetDeviceCommandLineOptions targetDeviceOptions;

  @AdditionalOptions
  @SuppressFieldNotInitialized
  private TestSelectorOptions testSelectorOptions;

  @AdditionalOptions
  @SuppressFieldNotInitialized
  private TestLabelOptions testLabelOptions;

  @Option(
      name = "--",
      usage = "When an external test runner is specified to be used (in the .buckconfig file), " +
          "all options specified after -- get forwarded directly to the external test runner. " +
          "Available options after -- are specific to that particular test runner and you may " +
          "also want to consult its help pages.",
      handler = ConsumeAllOptionsHandler.class)
  private List<String> withDashArguments = Lists.newArrayList();

  public boolean isRunAllTests() {
    return all || getArguments().isEmpty();
  }

  @Override
  public boolean isCodeCoverageEnabled() {
    return isCodeCoverageEnabled;
  }

  public TestRunningOptions.TestResultCacheMode getResultsCacheMode(BuckConfig buckConfig) {
    // The option is negative (--no-X) but we prefer to reason about positives, in the code.
    if (isResultsCacheDisabled == null) {
      boolean isUseResultsCache = buckConfig.getBooleanValue("test", USE_RESULTS_CACHE, true);
      isResultsCacheDisabled = !isUseResultsCache;
    }
    if (isOnlyFailing == null) {
      isOnlyFailing = buckConfig.getBooleanValue("test", RERUN_ONLY_FAILING, false);
    }

    if (isCodeCoverageEnabled()) {
      return TestRunningOptions.TestResultCacheMode.DISABLED;
    }

    if (isResultsCacheDisabled) {
      return TestRunningOptions.TestResultCacheMode.DISABLED;
    }
    if (isOnlyFailing) {
      return TestRunningOptions.TestResultCacheMode.ENABLED_IF_PASSED;
    }
    return TestRunningOptions.TestResultCacheMode.ENABLED;
  }

  @Override
  public boolean isDebugEnabled() {
    return isDebugEnabled;
  }

  public Optional<TargetDevice> getTargetDeviceOptional() {
    return targetDeviceOptions.getTargetDeviceOptional();
  }

  public AdbOptions getAdbOptions(BuckConfig buckConfig) {
    return adbOptions.getAdbOptions(buckConfig);
  }

  public TargetDeviceOptions getTargetDeviceOptions() {
    return targetDeviceOptions.getTargetDeviceOptions();
  }

  public boolean isMatchedByLabelOptions(BuckConfig buckConfig, Set<Label> labels) {
    return testLabelOptions.isMatchedByLabelOptions(buckConfig, labels);
  }

  public boolean shouldExcludeTransitiveTests() {
    return shouldExcludeTransitiveTests;
  }

  public boolean shouldExcludeWin() {
    return testLabelOptions.shouldExcludeWin();
  }

  public boolean isBuildFiltered(BuckConfig buckConfig) {
    return isBuildFiltered != null ?
        isBuildFiltered :
        buckConfig.getBooleanValue("test", "build_filtered_tests", false);
  }

  public int getNumTestThreads(BuckConfig buckConfig) {
    if (isDebugEnabled()) {
      return 1;
    }
    return buckConfig.getNumThreads();
  }

  public int getNumTestManagedThreads(BuckConfig buckConfig) {
    if (isDebugEnabled()) {
      return 1;
    }
    return buckConfig.getManagedThreadCount();
  }

  private TestRunningOptions getTestRunningOptions(CommandRunnerParams params) {
    TestRunningOptions.Builder builder = TestRunningOptions.builder()
        .setCodeCoverageEnabled(isCodeCoverageEnabled)
        .setRunAllTests(isRunAllTests())
        .setTestSelectorList(testSelectorOptions.getTestSelectorList())
        .setShouldExplainTestSelectorList(testSelectorOptions.shouldExplain())
        .setTestResultCacheMode(getResultsCacheMode(params.getBuckConfig()))
        .setShufflingTests(isShufflingTests)
        .setPathToXmlTestOutput(Optional.ofNullable(pathToXmlTestOutput))
        .setPathToJavaAgent(Optional.ofNullable(pathToJavaAgent))
        .setCoverageReportFormat(coverageReportFormat)
        .setCoverageReportTitle(coverageReportTitle)
        .setEnvironmentOverrides(environmentOverrides);

    Optional<ImmutableList<String>> coverageIncludes =
        params.getBuckConfig().getOptionalListWithoutComments("test", "coverageIncludes", ',');
    if (coverageIncludes.isPresent()) {
      builder.setCoverageIncludes(Joiner.on(",").join(coverageIncludes.get()));
    }

    Optional<ImmutableList<String>> coverageExcludes =
        params.getBuckConfig().getOptionalListWithoutComments("test", "coverageExcludes", ',');
    if (coverageExcludes.isPresent()) {
      builder.setCoverageExcludes(Joiner.on(",").join(coverageExcludes.get()));
    }
    return builder.build();
  }

  private int runTestsInternal(
      CommandRunnerParams params,
      BuildEngine buildEngine,
      Build build,
      Iterable<TestRule> testRules)
      throws InterruptedException, IOException {

    if (!withDashArguments.isEmpty()) {
      params.getBuckEventBus().post(ConsoleEvent.severe(
          "Unexpected arguments after \"--\" when using internal runner"));
      return 1;
    }

    ConcurrencyLimit concurrencyLimit = new ConcurrencyLimit(
        getNumTestThreads(params.getBuckConfig()),
        params.getBuckConfig().getLoadLimit(),
        params.getBuckConfig().getResourceAllocationFairness(),
        getNumTestManagedThreads(params.getBuckConfig()),
        params.getBuckConfig().getDefaultResourceAmounts(),
        params.getBuckConfig().getMaximumResourceAmounts());
    try (
        CommandThreadManager testPool = new CommandThreadManager(
            "Test-Run",
            concurrencyLimit)) {
      SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(build.getRuleResolver());
      return TestRunning.runTests(
          params,
          testRules,
          build.getExecutionContext(),
          getTestRunningOptions(params),
          testPool.getExecutor(),
          buildEngine,
          new DefaultStepRunner(),
          new SourcePathResolver(ruleFinder),
          ruleFinder);
    } catch (ExecutionException e) {
      params.getBuckEventBus().post(ConsoleEvent.severe(
          MoreExceptions.getHumanReadableOrLocalizedMessage(e)));
      return 1;
    }
  }

  private int runTestsExternal(
      final CommandRunnerParams params,
      Build build,
      Iterable<String> command,
      Iterable<TestRule> testRules,
      SourcePathResolver pathResolver)
      throws InterruptedException, IOException {
    TestRunningOptions options = getTestRunningOptions(params);

    // Walk the test rules, collecting all the specs.
    List<ExternalTestRunnerTestSpec> specs = Lists.newArrayList();
    for (TestRule testRule : testRules) {
      if (!(testRule instanceof ExternalTestRunnerRule)) {
        params.getBuckEventBus().post(ConsoleEvent.severe(String.format(
            "Test %s does not support external test running",
            testRule.getBuildTarget())));
        return 1;
      }
      ExternalTestRunnerRule rule = (ExternalTestRunnerRule) testRule;
      specs.add(rule.getExternalTestRunnerSpec(build.getExecutionContext(), options, pathResolver));
    }

    // Serialize the specs to a file to pass into the test runner.
    Path infoFile =
        params.getCell().getFilesystem()
            .resolve(params.getCell().getFilesystem().getBuckPaths().getScratchDir())
            .resolve("external_runner_specs.json");
    Files.createDirectories(infoFile.getParent());
    Files.deleteIfExists(infoFile);
    params.getObjectMapper().writerWithDefaultPrettyPrinter().writeValue(infoFile.toFile(), specs);

    // Launch and run the external test runner, forwarding it's stdout/stderr to the console.
    // We wait for it to complete then returns its error code.
    ListeningProcessExecutor processExecutor = new ListeningProcessExecutor();
    ProcessExecutorParams processExecutorParams =
        ProcessExecutorParams.builder()
            .addAllCommand(command)
            .addAllCommand(withDashArguments)
            .setEnvironment(params.getEnvironment())
            .addCommand("--buck-test-info", infoFile.toString())
            .addCommand(
                "--jobs",
                String.valueOf(getConcurrencyLimit(params.getBuckConfig()).threadLimit))
            .setDirectory(params.getCell().getFilesystem().getRootPath())
            .build();
    ForwardingProcessListener processListener =
        new ForwardingProcessListener(
            Channels.newChannel(params.getConsole().getStdOut()),
            Channels.newChannel(params.getConsole().getStdErr()));
    ListeningProcessExecutor.LaunchedProcess process =
        processExecutor.launchProcess(processExecutorParams, processListener);
    try {
      return processExecutor.waitForProcess(process);
    } finally {
      processExecutor.destroyProcess(process, /* force */ false);
      processExecutor.waitForProcess(process);
    }
  }

  @Override
  public int runWithoutHelp(CommandRunnerParams params) throws IOException, InterruptedException {
    LOG.debug("Running with arguments %s", getArguments());

    try (CommandThreadManager pool = new CommandThreadManager(
        "Test",
        getConcurrencyLimit(params.getBuckConfig()))) {
      // Post the build started event, setting it to the Parser recorded start time if appropriate.
      BuildEvent.Started started = BuildEvent.started(getArguments());
      if (params.getParser().getParseStartTime().isPresent()) {
        params.getBuckEventBus().post(
            started,
            params.getParser().getParseStartTime().get());
      } else {
        params.getBuckEventBus().post(started);
      }

      // The first step is to parse all of the build files. This will populate the parser and find
      // all of the test rules.
      TargetGraphAndBuildTargets targetGraphAndBuildTargets;
      ParserConfig parserConfig = params.getBuckConfig().getView(ParserConfig.class);

      try {

        // If the user asked to run all of the tests, parse all of the build files looking for any
        // test rules.
        boolean ignoreBuckAutodepsFiles = false;
        if (isRunAllTests()) {
          targetGraphAndBuildTargets =
              params.getParser().buildTargetGraphForTargetNodeSpecs(
                  params.getBuckEventBus(),
                  params.getCell(),
                  getEnableParserProfiling(),
                  pool.getExecutor(),
                  ImmutableList.of(
                      TargetNodePredicateSpec.of(
                          input -> Description.getBuildRuleType(
                              input.getDescription()).isTestRule(),
                          BuildFileSpec.fromRecursivePath(
                              Paths.get(""),
                              params.getCell().getRoot()))),
                  ignoreBuckAutodepsFiles,
                  parserConfig.getDefaultFlavorsMode());
          targetGraphAndBuildTargets =
              targetGraphAndBuildTargets.withBuildTargets(ImmutableSet.of());

          // Otherwise, the user specified specific test targets to build and run, so build a graph
          // around these.
        } else {
          LOG.debug("Parsing graph for arguments %s", getArguments());
          targetGraphAndBuildTargets = params.getParser()
              .buildTargetGraphForTargetNodeSpecs(
                  params.getBuckEventBus(),
                  params.getCell(),
                  getEnableParserProfiling(),
                  pool.getExecutor(),
                  parseArgumentsAsTargetNodeSpecs(
                      params.getBuckConfig(),
                      getArguments()),
                  ignoreBuckAutodepsFiles,
                  parserConfig.getDefaultFlavorsMode());

          LOG.debug("Got explicit build targets %s", targetGraphAndBuildTargets.getBuildTargets());
          ImmutableSet.Builder<BuildTarget> testTargetsBuilder = ImmutableSet.builder();
          for (TargetNode<?, ?> node :
               targetGraphAndBuildTargets.getTargetGraph()
                   .getAll(targetGraphAndBuildTargets.getBuildTargets())) {
            ImmutableSortedSet<BuildTarget> nodeTests = TargetNodes.getTestTargetsForNode(node);
            if (!nodeTests.isEmpty()) {
              LOG.debug("Got tests for target %s: %s", node.getBuildTarget(), nodeTests);
              testTargetsBuilder.addAll(nodeTests);
            }
          }
          ImmutableSet<BuildTarget> testTargets = testTargetsBuilder.build();
          if (!testTargets.isEmpty()) {
            LOG.debug("Got related test targets %s, building new target graph...", testTargets);
            TargetGraph targetGraph =
                params.getParser().buildTargetGraph(
                    params.getBuckEventBus(),
                    params.getCell(),
                    getEnableParserProfiling(),
                    pool.getExecutor(),
                    Iterables.concat(
                        targetGraphAndBuildTargets.getBuildTargets(),
                        testTargets));
            LOG.debug("Finished building new target graph with tests.");
            targetGraphAndBuildTargets = targetGraphAndBuildTargets.withTargetGraph(targetGraph);
          }
        }

        if (params.getBuckConfig().getBuildVersions()) {
          targetGraphAndBuildTargets = toVersionedTargetGraph(params, targetGraphAndBuildTargets);
        }

      } catch (BuildTargetException | BuildFileParseException | VersionException e) {
        params.getBuckEventBus().post(ConsoleEvent.severe(
            MoreExceptions.getHumanReadableOrLocalizedMessage(e)));
        return 1;
      }

      ActionGraphAndResolver actionGraphAndResolver = Preconditions.checkNotNull(
          params.getActionGraphCache().getActionGraph(
              params.getBuckEventBus(),
              params.getBuckConfig().isActionGraphCheckingEnabled(),
              targetGraphAndBuildTargets.getTargetGraph(),
              params.getBuckConfig().getKeySeed()));
      // Look up all of the test rules in the action graph.
      Iterable<TestRule> testRules = Iterables.filter(
          actionGraphAndResolver.getActionGraph().getNodes(),
          TestRule.class);

      // Unless the user requests that we build filtered tests, filter them out here, before
      // the build.
      if (!isBuildFiltered(params.getBuckConfig())) {
        testRules =
            filterTestRules(
                params.getBuckConfig(),
                targetGraphAndBuildTargets.getBuildTargets(),
                testRules);
      }

      CachingBuildEngineBuckConfig cachingBuildEngineBuckConfig =
          params.getBuckConfig().getView(CachingBuildEngineBuckConfig.class);
      try (CommandThreadManager artifactFetchService = getArtifactFetchService(
          params.getBuckConfig(), pool.getExecutor())) {
        LocalCachingBuildEngineDelegate localCachingBuildEngineDelegate =
            new LocalCachingBuildEngineDelegate(params.getFileHashCache());
        CachingBuildEngine cachingBuildEngine =
            new CachingBuildEngine(
                new LocalCachingBuildEngineDelegate(params.getFileHashCache()),
                pool.getExecutor(),
                artifactFetchService == null ?
                    pool.getExecutor() :
                    artifactFetchService.getExecutor(),
                new DefaultStepRunner(),
                getBuildEngineMode().orElse(cachingBuildEngineBuckConfig.getBuildEngineMode()),
                cachingBuildEngineBuckConfig.getBuildDepFiles(),
                cachingBuildEngineBuckConfig.getBuildMaxDepFileCacheEntries(),
                cachingBuildEngineBuckConfig.getBuildArtifactCacheSizeLimit(),
                params.getObjectMapper(),
                actionGraphAndResolver.getResolver(),
                cachingBuildEngineBuckConfig.getResourceAwareSchedulingInfo(),
                new RuleKeyFactoryManager(
                    params.getBuckConfig().getKeySeed(),
                    localCachingBuildEngineDelegate.createFileHashCacheLoader()::getUnchecked,
                    actionGraphAndResolver.getResolver(),
                    cachingBuildEngineBuckConfig.getBuildInputRuleKeyFileSizeLimit()));
        try (Build build = createBuild(
            params.getBuckConfig(),
            actionGraphAndResolver.getActionGraph(),
            actionGraphAndResolver.getResolver(),
            params.getCell(),
            params.getAndroidPlatformTargetSupplier(),
            cachingBuildEngine,
            params.getArtifactCacheFactory().newInstance(),
            params.getConsole(),
            params.getBuckEventBus(),
            getTargetDeviceOptional(),
            params.getPersistentWorkerPools(),
            params.getPlatform(),
            params.getEnvironment(),
            params.getObjectMapper(),
            params.getClock(),
            Optional.of(getAdbOptions(params.getBuckConfig())),
            Optional.of(getTargetDeviceOptions()),
            params.getExecutors())) {

          // Build all of the test rules.
          int exitCode = build.executeAndPrintFailuresToEventBus(
              RichStream.from(testRules)
                  .map(TestRule::getBuildTarget)
                  .collect(MoreCollectors.toImmutableList()),
              isKeepGoing(),
              params.getBuckEventBus(),
              params.getConsole(),
              getPathToBuildReport(params.getBuckConfig()));
          params.getBuckEventBus().post(BuildEvent.finished(started, exitCode));
          if (exitCode != 0) {
            return exitCode;
          }

          // If the user requests that we build tests that we filter out, then we perform
          // the filtering here, after we've done the build but before we run the tests.
          if (isBuildFiltered(params.getBuckConfig())) {
            testRules =
                filterTestRules(
                    params.getBuckConfig(),
                    targetGraphAndBuildTargets.getBuildTargets(),
                    testRules);
          }

          // Once all of the rules are built, then run the tests.
          Optional<ImmutableList<String>> externalTestRunner =
              params.getBuckConfig().getExternalTestRunner();
          if (externalTestRunner.isPresent()) {
            SourcePathResolver pathResolver = new SourcePathResolver(
                new SourcePathRuleFinder(actionGraphAndResolver.getResolver()));
            return runTestsExternal(
                params,
                build,
                externalTestRunner.get(),
                testRules,
                pathResolver);
          }
          return runTestsInternal(params, cachingBuildEngine, build, testRules);
        }
      }
    }
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @VisibleForTesting
  Iterable<TestRule> filterTestRules(
      BuckConfig buckConfig,
      ImmutableSet<BuildTarget> explicitBuildTargets,
      Iterable<TestRule> testRules) {

    ImmutableSortedSet.Builder<TestRule> builder =
        ImmutableSortedSet.orderedBy(
            (o1, o2) -> o1.getBuildTarget().getFullyQualifiedName().compareTo(
                o2.getBuildTarget().getFullyQualifiedName()));

    for (TestRule rule : testRules) {
      boolean explicitArgument = explicitBuildTargets.contains(rule.getBuildTarget());
      boolean matchesLabel = isMatchedByLabelOptions(buckConfig, rule.getLabels());

      // We always want to run the rules that are given on the command line. Always. Unless we don't
      // want to.
      if (shouldExcludeWin() && !matchesLabel) {
        continue;
      }

      // The testRules Iterable contains transitive deps of the arguments given on the command line,
      // filter those out if such is the user's will.
      if (shouldExcludeTransitiveTests() && !explicitArgument) {
        continue;
      }

      // Normal behavior is to include all rules that match the given label as well as any that
      // were explicitly specified by the user.
      if (explicitArgument || matchesLabel) {
        builder.add(rule);
      }
    }

    return builder.build();
  }

  @Override
  protected void printUsage(PrintStream stream) {
    stream.println("Usage:");
    stream.println("  " + "buck test [<targets>] [<options>]");
    stream.println();

    stream.println("Description:");
    stream.println("  Builds and runs the tests for one or more specified targets.");
    stream.println("  You can either directly specify test targets, or any other target which");
    stream.println("  contains a `tests = ['...']` field to specify its tests. Alternatively,");
    stream.println("  by specifying no targets all of the tests will be run.");
    stream.println("  Tests get run by the internal test runner unless an external test runner");
    stream.println("  is specified in the .buckconfig file. Note that not all of the options");
    stream.println("  are applicable to all build rule types. Likewise, when an external test");
    stream.println("  runner is being used, some of the options listed here may not apply, and");
    stream.println("  you may need to use options specific to that test runner. See -- option.");
    stream.println();

    stream.println("Options:");
    new AdditionalOptionsCmdLineParser(this).printUsage(stream);
    stream.println();
  }

  @Override
  public String getShortDescription() {
    return "builds and runs the tests for the specified target";
  }
}
