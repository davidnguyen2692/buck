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

import com.facebook.buck.config.BuckConfig;
import com.facebook.buck.config.resources.ResourcesConfig;
import com.facebook.buck.core.cell.CellConfig;
import com.facebook.buck.core.cell.name.RelativeCellName;
import com.facebook.buck.core.cell.resolver.CellPathResolver;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.targetgraph.TargetGraphAndBuildTargets;
import com.facebook.buck.core.rulekey.RuleKey;
import com.facebook.buck.event.BuckEventListener;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.log.LogConfigSetup;
import com.facebook.buck.parser.BuildTargetParser;
import com.facebook.buck.parser.BuildTargetPatternParser;
import com.facebook.buck.parser.BuildTargetPatternTargetNodeParser;
import com.facebook.buck.parser.TargetNodeSpec;
import com.facebook.buck.rules.keys.DefaultRuleKeyCache;
import com.facebook.buck.rules.keys.EventPostingRuleKeyCacheScope;
import com.facebook.buck.rules.keys.RuleKeyCacheRecycler;
import com.facebook.buck.rules.keys.RuleKeyCacheScope;
import com.facebook.buck.rules.keys.TrackedRuleKeyCache;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.ExecutorPool;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.cache.InstrumentingCacheStatsTracker;
import com.facebook.buck.util.concurrent.ConcurrencyLimit;
import com.facebook.buck.versions.VersionException;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.build.lib.clock.Clock;
import com.google.devtools.build.lib.clock.JavaClock;
import com.google.devtools.build.lib.profiler.Profiler;
import com.google.devtools.build.lib.profiler.Profiler.Format;
import com.google.devtools.build.lib.profiler.Profiler.ProfiledTaskKinds;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.NamedOptionDef;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;

public abstract class AbstractCommand implements Command {

  private static final String HELP_LONG_ARG = "--help";
  private static final String NO_CACHE_LONG_ARG = "--no-cache";
  private static final String OUTPUT_TEST_EVENTS_TO_FILE_LONG_ARG = "--output-test-events-to-file";
  private static final String PROFILE_PARSER_LONG_ARG = "--profile-buck-parser";
  private static final String NUM_THREADS_LONG_ARG = "--num-threads";
  private static final String CONFIG_LONG_ARG = "--config";
  private static final String SKYLARK_PROFILE_LONG_ARG = "--skylark-profile-output";

  /**
   * Contains all options defined in this class. These options are considered global since they are
   * known to all commands that inherit from this class.
   *
   * <p>The main purpose of having this list is to provide more structured help.
   */
  private static final ImmutableSet<String> GLOBAL_OPTIONS =
      ImmutableSet.of(
          HELP_LONG_ARG,
          NO_CACHE_LONG_ARG,
          OUTPUT_TEST_EVENTS_TO_FILE_LONG_ARG,
          PROFILE_PARSER_LONG_ARG,
          NUM_THREADS_LONG_ARG,
          CONFIG_LONG_ARG,
          VerbosityParser.VERBOSE_LONG_ARG,
          SKYLARK_PROFILE_LONG_ARG);

  /**
   * This value should never be read. {@link VerbosityParser} should be used instead. args4j
   * requires that all options that could be passed in are listed as fields, so we include this
   * field so that {@code --verbose} is universally available to all commands.
   */
  @Option(
      name = VerbosityParser.VERBOSE_LONG_ARG,
      aliases = {VerbosityParser.VERBOSE_SHORT_ARG},
      usage = "Specify a number between 0 and 8. '-v 1' is default, '-v 8' is most verbose.")
  @SuppressWarnings("PMD.UnusedPrivateField")
  private int verbosityLevel = -1;

  private volatile ExecutionContext executionContext;

  @Option(name = NUM_THREADS_LONG_ARG, aliases = "-j", usage = "Default is 1.25 * num processors.")
  @Nullable
  private Integer numThreads = null;

  @Option(
      name = CONFIG_LONG_ARG,
      aliases = {"-c"},
      usage = "Override .buckconfig option",
      metaVar = "section.option=value")
  private Map<String, String> configOverrides = new LinkedHashMap<>();

  @Option(
      name = SKYLARK_PROFILE_LONG_ARG,
      usage =
          "Experimental. Path to a file where Skylark profile information should be written into."
              + " The output is in Chrome Tracing format and can be viewed in chrome://tracing tab",
      metaVar = "PATH")
  @Nullable
  private String skylarkProfile;

  @Override
  public CellConfig getConfigOverrides() {
    CellConfig.Builder builder = CellConfig.builder();

    // Parse command-line config overrides.
    for (Map.Entry<String, String> entry : configOverrides.entrySet()) {
      List<String> key = Splitter.on("//").limit(2).splitToList(entry.getKey());
      RelativeCellName cellName = RelativeCellName.ALL_CELLS_SPECIAL_NAME;
      String configKey = key.get(0);
      if (key.size() == 2) {
        // Here we explicitly take the whole string as the cell name. We don't support transitive
        // path overrides for cells.
        if (key.get(0).length() == 0) {
          cellName = RelativeCellName.ROOT_CELL_NAME;
        } else {
          cellName = RelativeCellName.of(ImmutableSet.of(key.get(0)));
        }
        configKey = key.get(1);
      }
      int separatorIndex = configKey.lastIndexOf('.');
      if (separatorIndex < 0 || separatorIndex == configKey.length() - 1) {
        throw new HumanReadableException(
            "Invalid config override \"%s=%s\" Expected <section>.<field>=<value>.",
            configKey, entry.getValue());
      }
      String value = entry.getValue();
      // If the value is empty, un-set the config
      if (value == null) {
        value = "";
      }

      // Overrides for locations of transitive children of cells are weird as the order of overrides
      // can affect the result (for example `-c a/b/c.k=v -c a/b//repositories.c=foo` causes an
      // interesting problem as the a/b/c cell gets created as a side-effect of the first override,
      // but the second override wants to change its identity).
      // It's generally a better idea to use the .buckconfig.local mechanism when overriding
      // repositories anyway, so here we simply disallow them.
      String section = configKey.substring(0, separatorIndex);
      if (section.equals("repositories")) {
        throw new HumanReadableException(
            "Overriding repository locations from the command line "
                + "is not supported. Please place a .buckconfig.local in the appropriate location and "
                + "use that instead.");
      }
      String field = configKey.substring(separatorIndex + 1);
      builder.put(cellName, section, field, value);
    }
    if (numThreads != null) {
      builder.put(
          RelativeCellName.ALL_CELLS_SPECIAL_NAME, "build", "threads", String.valueOf(numThreads));
    }
    if (noCache) {
      builder.put(RelativeCellName.ALL_CELLS_SPECIAL_NAME, "cache", "mode", "");
    }

    return builder.build();
  }

  @Override
  public LogConfigSetup getLogConfig() {
    return LogConfigSetup.DEFAULT_SETUP;
  }

  @Option(
      name = NO_CACHE_LONG_ARG,
      usage = "Whether to ignore the remote & local cache declared in .buckconfig.")
  private boolean noCache = false;

  @Nullable
  @Option(
      name = OUTPUT_TEST_EVENTS_TO_FILE_LONG_ARG,
      aliases = {"--output-events-to-file"},
      usage =
          "Serialize test-related event-bus events to the given file "
              + "as line-oriented JSON objects.")
  private String eventsOutputPath = null;

  @Option(
      name = PROFILE_PARSER_LONG_ARG,
      usage =
          "Enable profiling of buck.py internals (not the target being compiled) in the debug"
              + "log and trace.")
  private boolean enableParserProfiling = false;

  @Option(name = HELP_LONG_ARG, usage = "Prints the available options and exits.")
  private boolean help = false;

  /** @return {code true} if the {@code [cache]} in {@code .buckconfig} should be ignored. */
  public boolean isNoCache() {
    return noCache;
  }

  public Optional<Path> getEventsOutputPath() {
    if (eventsOutputPath == null) {
      return Optional.empty();
    } else {
      return Optional.of(Paths.get(eventsOutputPath));
    }
  }

  /** Handle CmdLineException when calling parseArguments() */
  public void handleException(CmdLineException e) throws CmdLineException {
    throw e;
  }

  /** Print error message when there are unknown options */
  protected void handleException(CmdLineException e, String printedErrorMessage)
      throws CmdLineException {
    String message = e.getMessage();
    if (message != null && message.endsWith("is not a valid option")) {
      throw new CmdLineException(
          e.getParser(), String.format("%s\n%s", message, printedErrorMessage), e.getCause());
    } else {
      throw e;
    }
  }

  @Override
  public void printUsage(PrintStream stream) {
    CommandHelper.printShortDescription(this, stream);
    CmdLineParser parser = new AdditionalOptionsCmdLineParser(this);

    stream.println("Global options:");
    parser.printUsage(new OutputStreamWriter(stream), null, AbstractCommand::isGlobalOption);
    stream.println();

    stream.println("Options:");
    parser.printUsage(
        new OutputStreamWriter(stream),
        null,
        optionHandler -> !AbstractCommand.isGlobalOption(optionHandler));
    stream.println();
  }

  private static boolean isGlobalOption(OptionHandler<?> optionHandler) {
    OptionDef option = optionHandler.option;
    if (option instanceof NamedOptionDef) {
      NamedOptionDef namedOption = (NamedOptionDef) option;
      return GLOBAL_OPTIONS.contains(namedOption.name());
    }
    return false;
  }

  @Override
  public Optional<ExitCode> runHelp(PrintStream stream) {
    if (help) {
      printUsage(stream);
      return Optional.of(ExitCode.SUCCESS);
    }
    return Optional.empty();
  }

  @Override
  public final ExitCode run(CommandRunnerParams params) throws IOException, InterruptedException {
    if (help) {
      printUsage(params.getConsole().getStdOut());
      return ExitCode.SUCCESS;
    }
    if (params.getConsole().getAnsi().isAnsiTerminal()) {
      ImmutableList<String> motd = params.getBuckConfig().getMessageOfTheDay();
      if (!motd.isEmpty()) {
        for (String line : motd) {
          params.getBuckEventBus().post(ConsoleEvent.info(line));
        }
      }
    }
    try (Closeable closeable = prepareExecutionContext(params)) {
      return runWithoutHelp(params);
    }
  }

  protected Closeable prepareExecutionContext(CommandRunnerParams params) {
    executionContext = createExecutionContext(params);
    return () -> {
      ExecutionContext context = executionContext;
      executionContext = null;
      context.close();
    };
  }

  public abstract ExitCode runWithoutHelp(CommandRunnerParams params)
      throws IOException, InterruptedException;

  protected CommandLineBuildTargetNormalizer getCommandLineBuildTargetNormalizer(
      BuckConfig buckConfig) {
    return new CommandLineBuildTargetNormalizer(buckConfig);
  }

  public boolean getEnableParserProfiling() {
    return enableParserProfiling;
  }

  public ImmutableList<TargetNodeSpec> parseArgumentsAsTargetNodeSpecs(
      BuckConfig config, Iterable<String> targetsAsArgs) {
    ImmutableList.Builder<TargetNodeSpec> specs = ImmutableList.builder();
    CommandLineTargetNodeSpecParser parser =
        new CommandLineTargetNodeSpecParser(config, new BuildTargetPatternTargetNodeParser());
    for (String arg : targetsAsArgs) {
      specs.addAll(parser.parse(config.getCellPathResolver(), arg));
    }
    return specs.build();
  }

  /**
   * @param cellNames
   * @param buildTargetNames The build targets to parse, represented as strings.
   * @return A set of {@link BuildTarget}s for the input buildTargetNames.
   */
  protected ImmutableSet<BuildTarget> getBuildTargets(
      CellPathResolver cellNames, Iterable<String> buildTargetNames) {
    ImmutableSet.Builder<BuildTarget> buildTargets = ImmutableSet.builder();

    // Parse all of the build targets specified by the user.
    for (String buildTargetName : buildTargetNames) {
      buildTargets.add(
          BuildTargetParser.INSTANCE.parse(
              buildTargetName, BuildTargetPatternParser.fullyQualified(), cellNames));
    }

    return buildTargets.build();
  }

  protected ExecutionContext getExecutionContext() {
    return executionContext;
  }

  private ExecutionContext createExecutionContext(CommandRunnerParams params) {
    return getExecutionContextBuilder(params).build();
  }

  protected ExecutionContext.Builder getExecutionContextBuilder(CommandRunnerParams params) {
    ExecutionContext.Builder builder =
        ExecutionContext.builder()
            .setConsole(params.getConsole())
            .setBuckEventBus(params.getBuckEventBus())
            .setPlatform(params.getPlatform())
            .setEnvironment(params.getEnvironment())
            .setJavaPackageFinder(params.getJavaPackageFinder())
            .setExecutors(params.getExecutors())
            .setCellPathResolver(params.getCell().getCellPathResolver())
            .setBuildCellRootPath(params.getCell().getRoot())
            .setProcessExecutor(new DefaultProcessExecutor(params.getConsole()))
            .setDefaultTestTimeoutMillis(params.getBuckConfig().getDefaultTestTimeoutMillis())
            .setInclNoLocationClassesEnabled(
                params.getBuckConfig().getBooleanValue("test", "incl_no_location_classes", false))
            .setRuleKeyDiagnosticsMode(params.getBuckConfig().getRuleKeyDiagnosticsMode())
            .setConcurrencyLimit(getConcurrencyLimit(params.getBuckConfig()))
            .setPersistentWorkerPools(params.getPersistentWorkerPools())
            .setProjectFilesystemFactory(params.getProjectFilesystemFactory());
    if (skylarkProfile != null) {
      Clock clock = new JavaClock();
      try {
        OutputStream outputStream =
            new BufferedOutputStream(Files.newOutputStream(Paths.get(skylarkProfile)));
        Profiler.instance()
            .start(
                ProfiledTaskKinds.ALL,
                outputStream,
                Format.JSON_TRACE_FILE_FORMAT,
                "Buck profile for " + skylarkProfile + " at " + LocalDate.now(),
                false,
                clock,
                clock.nanoTime());
      } catch (IOException e) {
        throw new HumanReadableException(
            "Cannot initialize Skylark profiler for " + skylarkProfile, e);
      }
      builder.setProfiler(Optional.of(Profiler.instance()));
    }
    return builder;
  }

  public ConcurrencyLimit getConcurrencyLimit(BuckConfig buckConfig) {
    return buckConfig.getView(ResourcesConfig.class).getConcurrencyLimit();
  }

  @Override
  public boolean isSourceControlStatsGatheringEnabled() {
    return false;
  }

  TargetGraphAndBuildTargets toVersionedTargetGraph(
      CommandRunnerParams params, TargetGraphAndBuildTargets targetGraphAndBuildTargets)
      throws VersionException, InterruptedException {
    return params
        .getVersionedTargetGraphCache()
        .toVersionedTargetGraph(
            params.getBuckEventBus(),
            params.getBuckConfig(),
            params.getTypeCoercerFactory(),
            targetGraphAndBuildTargets);
  }

  @Override
  public Iterable<BuckEventListener> getEventListeners(
      Map<ExecutorPool, ListeningExecutorService> executorPool,
      ScheduledExecutorService scheduledExecutorService) {
    return ImmutableList.of();
  }

  RuleKeyCacheScope<RuleKey> getDefaultRuleKeyCacheScope(
      CommandRunnerParams params, RuleKeyCacheRecycler.SettingsAffectingCache settings) {
    return params
        .getDefaultRuleKeyFactoryCacheRecycler()
        // First try to get the cache from the recycler.
        .map(recycler -> recycler.withRecycledCache(params.getBuckEventBus(), settings))
        // Otherwise, create a new one.
        .orElseGet(
            () ->
                new EventPostingRuleKeyCacheScope<>(
                    params.getBuckEventBus(),
                    new TrackedRuleKeyCache<>(
                        new DefaultRuleKeyCache<>(), new InstrumentingCacheStatsTracker())));
  }

  @Override
  public boolean performsBuild() {
    return false;
  }

  /**
   * Converts target arguments to fully qualified form (including resolving aliases, resolving the
   * implicit package target, etc).
   */
  protected ImmutableSet<BuildTarget> convertArgumentsToBuildTargets(
      CommandRunnerParams params, List<String> arguments) {
    return getCommandLineBuildTargetNormalizer(params.getBuckConfig())
        .normalizeAll(arguments)
        .stream()
        .map(
            input ->
                BuildTargetParser.INSTANCE.parse(
                    input,
                    BuildTargetPatternParser.fullyQualified(),
                    params.getCell().getCellPathResolver()))
        .collect(ImmutableSet.toImmutableSet());
  }
}
