/*
 * Copyright 2017-present Facebook, Inc.
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

import com.facebook.buck.core.model.actiongraph.ActionGraph;
import com.facebook.buck.core.model.actiongraph.ActionGraphAndBuilder;
import com.facebook.buck.core.model.actiongraph.computation.ActionGraphConfig;
import com.facebook.buck.core.model.targetgraph.TargetGraphAndBuildTargets;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.rules.attr.HasRuntimeDeps;
import com.facebook.buck.core.util.graph.DirectedAcyclicGraph;
import com.facebook.buck.core.util.graph.Dot;
import com.facebook.buck.core.util.graph.MutableDirectedGraph;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.log.Logger;
import com.facebook.buck.parser.ParserConfig;
import com.facebook.buck.parser.exceptions.BuildFileParseException;
import com.facebook.buck.util.DirtyPrintStreamDecorator;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.MoreExceptions;
import com.facebook.buck.util.RichStream;
import com.facebook.buck.versions.VersionException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/** Command that dumps basic information about the action graph. */
public class AuditActionGraphCommand extends AbstractCommand {
  private static final Logger LOG = Logger.get(AuditActionGraphCommand.class);

  @Option(name = "--dot", usage = "Print result in graphviz dot format.")
  private boolean generateDotOutput;

  @Option(name = "--include-runtime-deps", usage = "Include runtime deps in addition to build deps")
  private boolean includeRuntimeDeps;

  @Argument private List<String> targetSpecs = new ArrayList<>();

  @Override
  public ExitCode runWithoutHelp(CommandRunnerParams params)
      throws IOException, InterruptedException {
    try (CommandThreadManager pool =
        new CommandThreadManager("Audit", getConcurrencyLimit(params.getBuckConfig())); ) {
      // Create the target graph.
      TargetGraphAndBuildTargets unversionedTargetGraphAndBuildTargets =
          params
              .getParser()
              .buildTargetGraphForTargetNodeSpecs(
                  params.getBuckEventBus(),
                  params.getCell(),
                  getEnableParserProfiling(),
                  pool.getListeningExecutorService(),
                  parseArgumentsAsTargetNodeSpecs(
                      params.getCell().getCellPathResolver(), params.getBuckConfig(), targetSpecs),
                  params.getBuckConfig().getView(ParserConfig.class).getDefaultFlavorsMode());
      TargetGraphAndBuildTargets targetGraphAndBuildTargets =
          params.getBuckConfig().getBuildVersions()
              ? toVersionedTargetGraph(params, unversionedTargetGraphAndBuildTargets)
              : unversionedTargetGraphAndBuildTargets;

      // Create the action graph.
      ActionGraphAndBuilder actionGraphAndBuilder =
          params
              .getActionGraphCache()
              .getActionGraph(
                  params.getBuckEventBus(),
                  targetGraphAndBuildTargets.getTargetGraph(),
                  params.getCell().getCellProvider(),
                  params.getBuckConfig().getView(ActionGraphConfig.class),
                  params.getRuleKeyConfiguration(),
                  params.getPoolSupplier());
      SourcePathRuleFinder ruleFinder =
          new SourcePathRuleFinder(actionGraphAndBuilder.getActionGraphBuilder());

      // Dump the action graph.
      if (generateDotOutput) {
        dumpAsDot(
            actionGraphAndBuilder.getActionGraph(),
            actionGraphAndBuilder.getActionGraphBuilder(),
            ruleFinder,
            includeRuntimeDeps,
            params.getConsole().getStdOut());
      } else {
        dumpAsJson(
            actionGraphAndBuilder.getActionGraph(),
            actionGraphAndBuilder.getActionGraphBuilder(),
            ruleFinder,
            includeRuntimeDeps,
            params.getConsole().getStdOut());
      }
    } catch (BuildFileParseException | VersionException e) {
      // The exception should be logged with stack trace instead of only emitting the error.
      LOG.info(e, "Caught an exception and treating it as a command error.");
      params
          .getBuckEventBus()
          .post(ConsoleEvent.severe(MoreExceptions.getHumanReadableOrLocalizedMessage(e)));
      return ExitCode.PARSE_ERROR;
    }
    return ExitCode.SUCCESS;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public String getShortDescription() {
    return "Dump basic action graph node and connectivity information.";
  }

  /**
   * Dump basic information about the action graph to the given stream in a simple JSON format.
   *
   * <p>The passed in stream is not closed after this operation.
   */
  private static void dumpAsJson(
      ActionGraph graph,
      ActionGraphBuilder actionGraphBuilder,
      SourcePathRuleFinder ruleFinder,
      boolean includeRuntimeDeps,
      OutputStream out)
      throws IOException {
    try (JsonGenerator json =
        new JsonFactory()
            .createGenerator(out)
            .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)) {
      json.writeStartArray();
      for (BuildRule node : graph.getNodes()) {
        writeJsonObjectForBuildRule(json, node, actionGraphBuilder, ruleFinder, includeRuntimeDeps);
      }
      json.writeEndArray();
    }
  }

  private static void writeJsonObjectForBuildRule(
      JsonGenerator json,
      BuildRule node,
      ActionGraphBuilder actionGraphBuilder,
      SourcePathRuleFinder ruleFinder,
      boolean includeRuntimeDeps)
      throws IOException {
    json.writeStartObject();
    json.writeStringField("name", node.getFullyQualifiedName());
    json.writeStringField("type", node.getType());
    {
      json.writeArrayFieldStart("buildDeps");
      for (BuildRule dep : node.getBuildDeps()) {
        json.writeString(dep.getFullyQualifiedName());
      }
      json.writeEndArray();
      if (includeRuntimeDeps) {
        json.writeArrayFieldStart("runtimeDeps");
        for (BuildRule dep : getRuntimeDeps(node, actionGraphBuilder, ruleFinder)) {
          json.writeString(dep.getFullyQualifiedName());
        }
        json.writeEndArray();
      }
    }
    json.writeEndObject();
  }

  private static void dumpAsDot(
      ActionGraph graph,
      ActionGraphBuilder actionGraphBuilder,
      SourcePathRuleFinder ruleFinder,
      boolean includeRuntimeDeps,
      DirtyPrintStreamDecorator out)
      throws IOException {
    MutableDirectedGraph<BuildRule> dag = new MutableDirectedGraph<>();
    graph.getNodes().forEach(dag::addNode);
    graph.getNodes().forEach(from -> from.getBuildDeps().forEach(to -> dag.addEdge(from, to)));
    if (includeRuntimeDeps) {
      graph
          .getNodes()
          .forEach(
              from ->
                  getRuntimeDeps(from, actionGraphBuilder, ruleFinder)
                      .forEach(to -> dag.addEdge(from, to)));
    }
    Dot.builder(new DirectedAcyclicGraph<>(dag), "action_graph")
        .setNodeToName(BuildRule::getFullyQualifiedName)
        .setNodeToTypeName(BuildRule::getType)
        .build()
        .writeOutput(out);
  }

  private static SortedSet<BuildRule> getRuntimeDeps(
      BuildRule buildRule, ActionGraphBuilder actionGraphBuilder, SourcePathRuleFinder ruleFinder) {
    if (!(buildRule instanceof HasRuntimeDeps)) {
      return ImmutableSortedSet.of();
    }
    return actionGraphBuilder.getAllRules(
        RichStream.from(((HasRuntimeDeps) buildRule).getRuntimeDeps(ruleFinder)).toOnceIterable());
  }
}
