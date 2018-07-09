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

package com.facebook.buck.distributed.build_slave;

import com.facebook.buck.distributed.build_slave.DistributableBuildGraph.DistributableNode;
import com.facebook.buck.distributed.thrift.StampedeId;
import com.facebook.buck.log.Logger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * Trace of rule execution history per minion, from the coordinator's point of view. Generated by
 * {@link DistBuildTraceTracker}.
 */
public class DistBuildTrace {
  private static final Logger LOG = Logger.get(DistBuildTrace.class);

  public final StampedeId stampedeId;
  public final List<MinionTrace> minions;
  public final Optional<RuleTrace> criticalNode;

  public DistBuildTrace(
      StampedeId stampedeId,
      Map<String, List<RuleTrace>> rulesByMinionId,
      Optional<DistributableBuildGraph> buildGraph) {
    LOG.info("Starting to compute Stampede ChromeTrace.");
    this.stampedeId = stampedeId;
    this.minions = guessMinionThreadAssignment(rulesByMinionId);
    this.criticalNode =
        buildGraph.map(graph -> computeCriticalPaths(rulesByMinionId, graph).orElse(null));
    LOG.info("Finished computing Stampede ChromeTrace.");
  }

  @VisibleForTesting
  static List<MinionTrace> guessMinionThreadAssignment(
      Map<String, List<RuleTrace>> rulesByMinionId) {
    List<MinionTrace> minionTraces = new ArrayList<>(rulesByMinionId.size());

    rulesByMinionId
        .keySet()
        .forEach(
            minion -> {
              MinionTrace minionTrace = new MinionTrace(minion);
              minionTrace.recordItems(rulesByMinionId.get(minion));
              minionTraces.add(minionTrace);
            });

    return minionTraces;
  }

  @VisibleForTesting
  static Optional<RuleTrace> computeCriticalPaths(
      Map<String, List<RuleTrace>> rulesByMinionId, DistributableBuildGraph graph) {
    if (rulesByMinionId.size() == 0) {
      return Optional.empty();
    }

    // Collect all RuleTrace(s) into a map by name, picking the entries that finished later,
    // if there are multiple entries for the same name.
    Map<String, RuleTrace> traceMap =
        rulesByMinionId
            .values()
            .stream()
            .flatMap(List::stream)
            .collect(
                Collectors.toMap(
                    rule -> rule.ruleName,
                    rule -> rule,
                    BinaryOperator.maxBy(
                        Comparator.comparingLong(rule -> rule.finishEpochMillis))));

    List<RuleTrace> traceList = new ArrayList<>(traceMap.values());
    traceList.sort(Comparator.comparingLong(rule -> rule.startEpochMillis));
    RuleTrace critical = traceList.get(0);
    for (RuleTrace rule : traceList) {
      rule.bubbleUpDependencyChainInformation(traceMap, graph);

      if (rule.longestDependencyChainMillis > critical.longestDependencyChainMillis) {
        critical = rule;
      }
    }
    return Optional.of(critical);
  }

  /** Write trace in chrome trace format. */
  public void dumpToChromeTrace(Path chromeTrace) throws IOException {
    DistBuildChromeTraceRenderer.render(this, chromeTrace);
  }

  /** Single build rule information. */
  public static class RuleTrace {
    public final String ruleName;
    public final long startEpochMillis;
    public final long finishEpochMillis;
    public long longestDependencyChainMillis;
    public int numberOfDependents;
    public Optional<RuleTrace> previousRuleInLongestDependencyChain;

    RuleTrace(String ruleName, long startEpochMillis, long finishEpochMillis) {
      this.ruleName = ruleName;
      this.startEpochMillis = startEpochMillis;
      this.finishEpochMillis = finishEpochMillis;
      this.longestDependencyChainMillis = finishEpochMillis - startEpochMillis;
      this.numberOfDependents = 0;
      this.previousRuleInLongestDependencyChain = Optional.empty();
    }

    void bubbleUpDependencyChainInformation(
        Map<String, RuleTrace> allRuleTracesByName, DistributableBuildGraph buildGraph) {

      DistributableNode targetNode = buildGraph.getNode(ruleName);
      ImmutableSet<String> ancestors = targetNode.getTransitiveCacheableDependents(buildGraph);
      numberOfDependents = ancestors.size();

      for (String ancestor : ancestors) {
        RuleTrace dependentTrace = allRuleTracesByName.get(ancestor);
        if (dependentTrace == null) {
          LOG.debug("Couldn't find trace for rule: [%s].", ancestor);
          continue;
        }

        long ancestorDuration = dependentTrace.finishEpochMillis - dependentTrace.startEpochMillis;
        long chainLength = this.longestDependencyChainMillis + ancestorDuration;
        if (chainLength > dependentTrace.longestDependencyChainMillis) {
          dependentTrace.longestDependencyChainMillis = chainLength;
          dependentTrace.previousRuleInLongestDependencyChain = Optional.of(this);
        }
      }
    }
  }

  /** List of rules executed on a single thread in a minion. */
  public static class MinionThread {
    public final List<RuleTrace> ruleTraces = new ArrayList<>();
  }

  /** List of rules executed per thread in a single minion. */
  public static class MinionTrace {
    public final String minionId;
    public final List<MinionThread> threads = new ArrayList<>();

    MinionTrace(String minionId) {
      this.minionId = minionId;
    }

    void recordItems(List<RuleTrace> rules) {
      rules
          .stream()
          .sorted(Comparator.comparingLong(rule -> rule.startEpochMillis))
          .sequential()
          .forEach(
              rule -> {
                for (MinionThread thread : threads) {
                  RuleTrace lastRuleInThread = thread.ruleTraces.get(thread.ruleTraces.size() - 1);
                  if (lastRuleInThread.finishEpochMillis <= rule.startEpochMillis) {
                    thread.ruleTraces.add(rule);
                    return;
                  }
                }
                MinionThread newThread = new MinionThread();
                newThread.ruleTraces.add(rule);
                threads.add(newThread);
              });
    }
  }
}
