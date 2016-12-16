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

package com.facebook.buck.cli;

import com.facebook.buck.io.MorePaths;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.json.BuildFileParseException;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildFileTree;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetException;
import com.facebook.buck.model.FilesystemBackedBuildFileTree;
import com.facebook.buck.model.HasBuildTarget;
import com.facebook.buck.parser.PerBuildState;
import com.facebook.buck.query.QueryBuildTarget;
import com.facebook.buck.query.QueryEnvironment;
import com.facebook.buck.query.QueryException;
import com.facebook.buck.query.QueryExpression;
import com.facebook.buck.query.QueryFileTarget;
import com.facebook.buck.query.QueryTarget;
import com.facebook.buck.query.QueryTargetAccessor;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.TargetNodes;
import com.facebook.buck.util.MoreCollectors;
import com.facebook.buck.util.concurrent.MostExecutors;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The environment of a Buck query that can evaluate queries to produce a result.
 *
 * The query language is documented at docs/command/query.soy
 */
public class BuckQueryEnvironment implements QueryEnvironment {

  private static final Logger LOG = Logger.get(BuckQueryEnvironment.class);

  private final PerBuildState parserState;
  private final Cell rootCell;
  private final OwnersReport.Builder ownersReportBuilder;
  private final TargetPatternEvaluator targetPatternEvaluator;

  private final Map<Cell, BuildFileTree> buildFileTrees =  new HashMap<>();
  private final Map<BuildTarget, QueryTarget> buildTargetToQueryTarget = new HashMap<>();

  private TargetGraph graph = TargetGraph.EMPTY;

  private BuckQueryEnvironment(
      Cell rootCell,
      OwnersReport.Builder ownersReportBuilder,
      PerBuildState parserState,
      TargetPatternEvaluator targetPatternEvaluator) {
    this.parserState = parserState;
    this.rootCell = rootCell;
    this.ownersReportBuilder = ownersReportBuilder;
    this.buildFileTrees.put(
        rootCell,
        new FilesystemBackedBuildFileTree(
            rootCell.getFilesystem(),
            rootCell.getBuildFileName()));
    this.targetPatternEvaluator = targetPatternEvaluator;
  }

  public static BuckQueryEnvironment from(
      Cell rootCell,
      OwnersReport.Builder ownersReportBuilder,
      PerBuildState parserState,
      TargetPatternEvaluator targetPatternEvaluator) {
    return new BuckQueryEnvironment(
        rootCell, ownersReportBuilder, parserState, targetPatternEvaluator
    );
  }

  public static BuckQueryEnvironment from(
      CommandRunnerParams params, PerBuildState parserState, boolean enableProfiling) {
    return from(
        params.getCell(),
        OwnersReport.builder(
            params.getCell(),
            params.getParser(),
            params.getBuckEventBus(),
            params.getConsole()
        ),
        parserState,
        new TargetPatternEvaluator(
            params.getCell(),
            params.getBuckConfig(),
            params.getParser(),
            params.getBuckEventBus(),
            enableProfiling
        )
    );
  }

  public TargetGraph getTargetGraph() {
    return graph;
  }

  public PerBuildState getParserState() {
    return parserState;
  }

  public void preloadTargetPatterns(Iterable<String> patterns, ListeningExecutorService executor)
      throws QueryException, InterruptedException {
    try {
      targetPatternEvaluator.preloadTargetPatterns(patterns, executor);
    } catch (IOException e) {
      throw new QueryException(
          e,
          "Error in preloading targets. %s: %s",
          e.getClass(),
          e.getMessage());
    } catch (BuildTargetException | BuildFileParseException e) {
      throw new QueryException(e, "Error in preloading targets. %s", e.getMessage());
    }
  }

  /**
   * Evaluate the specified query expression in this environment.
   *
   * @return the resulting set of targets.
   * @throws QueryException if the evaluation failed.
   */
  public Set<QueryTarget> evaluateQuery(QueryExpression expr, ListeningExecutorService executor)
      throws QueryException, InterruptedException {
    Set<String> targetLiterals = new HashSet<>();
    expr.collectTargetPatterns(targetLiterals);
    preloadTargetPatterns(targetLiterals, executor);
    return expr.eval(this, executor);
  }

  public Set<QueryTarget> evaluateQuery(String query, ListeningExecutorService executor)
      throws QueryException, InterruptedException {
    return evaluateQuery(QueryExpression.parse(query, this), executor);
  }

  @Override
  public ImmutableSet<QueryTarget> getTargetsMatchingPattern(
      String pattern,
      ListeningExecutorService executor) throws QueryException, InterruptedException {
    try {
      return ImmutableSet.copyOf(
          Iterables.concat(
              targetPatternEvaluator.resolveTargetPatterns(ImmutableList.of(pattern), executor)
                  .values()));
    } catch (BuildTargetException | BuildFileParseException | IOException e) {
      throw new QueryException(e, "Error in resolving targets matching %s", pattern);
    }
  }

  TargetNode<?, ?> getNode(QueryTarget target)
      throws QueryException {
    if (!(target instanceof QueryBuildTarget)) {
      throw new IllegalArgumentException(String.format(
          "Expected %s to be a build target but it was an instance of %s",
          target,
          target.getClass().getName()));
    }
    ListeningExecutorService executor = null;
    try {
      executor = com.google.common.util.concurrent.MoreExecutors.listeningDecorator(
          MostExecutors.newSingleThreadExecutor("buck query.getNode"));
      return parserState.getTargetNode(((QueryBuildTarget) target).getBuildTarget());
    } catch (BuildTargetException | BuildFileParseException e) {
      throw new QueryException(e, "Error getting target node for %s\n%s", target, e.getMessage());
    } finally {
      if (executor != null) {
        executor.shutdown();
      }
    }
  }

  private QueryTarget getOrCreateQueryBuildTarget(BuildTarget buildTarget) {
    if (buildTargetToQueryTarget.containsKey(buildTarget)) {
      return buildTargetToQueryTarget.get(buildTarget);
    }
    QueryBuildTarget queryBuildTarget = QueryBuildTarget.of(buildTarget);
    buildTargetToQueryTarget.put(buildTarget, queryBuildTarget);
    return queryBuildTarget;
  }

  public ImmutableSet<QueryTarget> getTargetsFromBuildTargetsContainer(
      Iterable<? extends  HasBuildTarget> buildTargetsContainer) {
    ImmutableSortedSet.Builder<QueryTarget> builder = ImmutableSortedSet.naturalOrder();
    for (HasBuildTarget hasBuildTarget : buildTargetsContainer) {
      builder.add(getOrCreateQueryBuildTarget(hasBuildTarget.getBuildTarget()));
    }
    return builder.build();
  }

  public ImmutableSet<TargetNode<?, ?>> getNodesFromQueryTargets(Iterable<QueryTarget> input)
      throws QueryException {
    ImmutableSet.Builder<TargetNode<?, ?>> builder = ImmutableSet.builder();
    for (QueryTarget target : input) {
      builder.add(getNode(target));
    }
    return builder.build();
  }

  /** Given a set of target nodes, returns the build targets. */
  private static Set<BuildTarget> getTargetsFromNodes(Iterable<TargetNode<?, ?>> input) {
    Set<BuildTarget> result = new LinkedHashSet<>();
    for (TargetNode<?, ?> node : input) {
      result.add(node.getBuildTarget());
    }
    return result;
  }

  @Override
  public Set<QueryTarget> getFwdDeps(Iterable<QueryTarget> targets)
      throws QueryException, InterruptedException {
    Set<QueryTarget> result = new LinkedHashSet<>();
    for (QueryTarget target : targets) {
      TargetNode<?, ?> node = getNode(target);
      result.addAll(getTargetsFromBuildTargetsContainer(graph.getOutgoingNodesFor(node)));
    }
    return result;
  }

  @Override
  public Set<QueryTarget> getReverseDeps(Iterable<QueryTarget> targets)
      throws QueryException, InterruptedException {
    Set<QueryTarget> result = new LinkedHashSet<>();
    for (QueryTarget target : targets) {
      TargetNode<?, ?> node = getNode(target);
      result.addAll(getTargetsFromBuildTargetsContainer(graph.getIncomingNodesFor(node)));
    }
    return result;
  }

  @Override
  public Set<QueryTarget> getInputs(QueryTarget target) throws QueryException {
    TargetNode<?, ?> node = getNode(target);
    return node.getInputs().stream()
        .map(QueryFileTarget::of)
        .collect(MoreCollectors.toImmutableSet());
  }

  @Override
  public ImmutableSet<QueryTarget> getTransitiveClosure(Set<QueryTarget> targets)
      throws QueryException, InterruptedException {
    Set<TargetNode<?, ?>> nodes = new LinkedHashSet<>();
    for (QueryTarget target : targets) {
      nodes.add(getNode(target));
    }
    // Reusing the existing getSubgraph() for simplicity. It builds the graph when we only need the
    // nodes. The impact of creating the edges in terms of time and space should be minimal.
    return getTargetsFromBuildTargetsContainer(graph.getSubgraph(nodes).getNodes());
  }

  private void buildGraphForBuildTargets(Set<BuildTarget> targets)
      throws QueryException, InterruptedException {
    try {
      graph = parserState.buildTargetGraph(targets);
    } catch (BuildFileParseException | BuildTargetException | IOException e) {
      throw new QueryException(e, "Error in building dependency graph");
    }
  }

  @Override
  public void buildTransitiveClosure(
      Set<QueryTarget> targets,
      int maxDepth,
      ListeningExecutorService executor)
      throws QueryException, InterruptedException {
    // Filter QueryTargets that are build targets and not yet present in the build target graph.
    Set<BuildTarget> graphTargets = getTargetsFromNodes(graph.getNodes());
    Set<BuildTarget> newBuildTargets = new HashSet<>();
    for (QueryTarget target : targets) {
      if (target instanceof QueryBuildTarget) {
        BuildTarget buildTarget = ((QueryBuildTarget) target).getBuildTarget();
        if (!graphTargets.contains(buildTarget)) {
          newBuildTargets.add(buildTarget);
        }
      }
    }
    if (!newBuildTargets.isEmpty()) {
      buildGraphForBuildTargets(Sets.union(newBuildTargets, graphTargets));
      for (BuildTarget buildTarget : getTargetsFromNodes(graph.getNodes())) {
        if (!buildTargetToQueryTarget.containsKey(buildTarget)) {
          buildTargetToQueryTarget.put(buildTarget, QueryBuildTarget.of(buildTarget));
        }
      }
    }
  }

  @Override
  public ImmutableSet<QueryTarget> getTestsForTarget(QueryTarget target)
      throws QueryException, InterruptedException {
    return getTargetsFromBuildTargetsContainer(TargetNodes.getTestTargetsForNode(getNode(target)));
  }

  @Override
  public ImmutableSet<QueryTarget> getBuildFiles(Set<QueryTarget> targets)
      throws QueryException {
    final ProjectFilesystem cellFilesystem = rootCell.getFilesystem();
    final Path rootPath = cellFilesystem.getRootPath();
    Preconditions.checkState(rootPath.isAbsolute());

    ImmutableSet.Builder<QueryTarget> builder = ImmutableSet.builder();
    for (QueryTarget target : targets) {
      Preconditions.checkState(target instanceof QueryBuildTarget);
      BuildTarget buildTarget = ((QueryBuildTarget) target).getBuildTarget();
      Cell cell = rootCell.getCell(buildTarget);

      if (!buildFileTrees.containsKey(cell)) {
        LOG.info("Creating a new filesystem-backed build file tree for %s", cell.getRoot());
        buildFileTrees.put(
            cell,
            new FilesystemBackedBuildFileTree(
                cell.getFilesystem(),
                cell.getBuildFileName()));
      }
      BuildFileTree buildFileTree = Preconditions.checkNotNull(buildFileTrees.get(cell));
      Optional<Path> path = buildFileTree.getBasePathOfAncestorTarget(
          buildTarget.getBasePath());
      Preconditions.checkState(path.isPresent());

      Path buildFilePath = MorePaths.relativize(
          rootPath,
          cell.getFilesystem()
              .resolve(path.get())
              .resolve(cell.getBuildFileName()));
      Preconditions.checkState(cellFilesystem.exists(buildFilePath));
      builder.add(QueryFileTarget.of(buildFilePath));
    }
    return builder.build();
  }

  @Override
  public ImmutableSet<QueryTarget> getFileOwners(
      ImmutableList<String> files,
      ListeningExecutorService executor) throws InterruptedException, QueryException {
    try {
      BuildFileTree buildFileTree = Preconditions.checkNotNull(buildFileTrees.get(rootCell));
      OwnersReport report = ownersReportBuilder.build(buildFileTree, executor, files);
      return getTargetsFromBuildTargetsContainer(report.owners.keySet());
    } catch (BuildFileParseException | IOException e) {
      throw new QueryException(e, "Could not parse build targets.\n%s", e.getMessage());
    }
  }

  @Override
  public String getTargetKind(QueryTarget target) throws QueryException, InterruptedException {
    return Description.getBuildRuleType(getNode(target).getDescription()).getName();
  }

  @Override
  public ImmutableSet<QueryTarget> getTargetsInAttribute(QueryTarget target, String attribute)
      throws QueryException, InterruptedException {
    return QueryTargetAccessor.getTargetsInAttribute(getNode(target), attribute);
  }

  @Override
  public ImmutableSet<Object> filterAttributeContents(
      QueryTarget target,
      String attribute,
      final Predicate<Object> predicate)
      throws QueryException, InterruptedException {
    return QueryTargetAccessor.filterAttributeContents(getNode(target), attribute, predicate);
  }

  @Override
  public Iterable<QueryFunction> getFunctions() {
    return DEFAULT_QUERY_FUNCTIONS;
  }

}
