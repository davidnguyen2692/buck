/*
 * Copyright 2018-present Facebook, Inc.
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
package com.facebook.buck.distributed.build_client;

import com.facebook.buck.command.LocalBuildExecutorInvoker;
import com.facebook.buck.core.build.distributed.synchronization.impl.NoOpRemoteBuildRuleCompletionWaiter;
import com.facebook.buck.core.build.distributed.synchronization.impl.RemoteBuildRuleSynchronizer;
import com.facebook.buck.core.build.event.BuildEvent;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.distributed.ClientStatsTracker;
import com.facebook.buck.distributed.DistBuildService;
import com.facebook.buck.distributed.DistLocalBuildMode;
import com.facebook.buck.distributed.DistributedExitCode;
import com.facebook.buck.distributed.thrift.StampedeId;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.util.ExitCode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates the different parts of the build that run on client that launched Stampede. This
 * includes local build phases (racing, synchronized), and monitoring of distributed build.
 */
public class StampedeBuildClient {
  private static final Logger LOG = Logger.get(StampedeBuildClient.class);
  public static final String PENDING_STAMPEDE_ID = ClientStatsTracker.PENDING_STAMPEDE_ID;

  private final RemoteBuildRuleSynchronizer remoteBuildRuleSynchronizer;
  private final AtomicReference<StampedeId> stampedeIdReference =
      new AtomicReference<>(createPendingStampedeId());
  private final BuckEventBus eventBus;
  private final ClientStatsTracker clientStatsTracker;
  private final Optional<String> autoStampedeMessage;

  private final LocalBuildRunner racerBuildRunner;
  private final LocalBuildRunner synchronizedBuildRunner;
  private final DistBuildRunner distBuildRunner;
  private final LocalBuildExecutorInvoker localBuildExecutorInvoker;

  @VisibleForTesting
  public StampedeBuildClient(
      BuckEventBus eventBus,
      RemoteBuildRuleSynchronizer remoteBuildRuleSynchronizer,
      ExecutorService executorForLocalBuild,
      ExecutorService executorForDistBuildController,
      DistBuildService distBuildService,
      BuildEvent.DistBuildStarted distBuildStartedEvent,
      CountDownLatch waitForRacingBuildCalledLatch,
      CountDownLatch waitForSynchronizedBuildCalledLatch,
      LocalBuildExecutorInvoker localBuildExecutorInvoker,
      DistBuildControllerInvoker distBuildControllerInvoker,
      ClientStatsTracker clientStatsTracker,
      boolean waitGracefullyForDistributedBuildThreadToFinish,
      long distributedBuildThreadKillTimeoutSeconds,
      Optional<StampedeId> stampedeId) {
    stampedeId.ifPresent(this.stampedeIdReference::set);
    this.localBuildExecutorInvoker = localBuildExecutorInvoker;
    this.eventBus = eventBus;
    this.clientStatsTracker = clientStatsTracker;
    this.remoteBuildRuleSynchronizer = remoteBuildRuleSynchronizer;
    this.autoStampedeMessage = Optional.empty();
    this.racerBuildRunner =
        createStampedeLocalBuildRunner(
            executorForLocalBuild,
            localBuildExecutorInvoker,
            "racer",
            false,
            Optional.of(waitForRacingBuildCalledLatch));
    this.synchronizedBuildRunner =
        createStampedeLocalBuildRunner(
            executorForLocalBuild,
            localBuildExecutorInvoker,
            "synchronized",
            true,
            Optional.of(waitForSynchronizedBuildCalledLatch));
    this.distBuildRunner =
        createStampedeDistBuildExecutor(
            distBuildControllerInvoker,
            executorForDistBuildController,
            distBuildService,
            distBuildStartedEvent,
            waitGracefullyForDistributedBuildThreadToFinish,
            distributedBuildThreadKillTimeoutSeconds);
  }

  public StampedeBuildClient(
      BuckEventBus eventBus,
      ListeningExecutorService executorForLocalBuild,
      ListeningExecutorService executorForDistBuildController,
      DistBuildService distBuildService,
      BuildEvent.DistBuildStarted distBuildStartedEvent,
      LocalBuildExecutorInvoker localBuildExecutorInvoker,
      DistBuildControllerArgs.Builder distBuildControllerArgsBuilder,
      DistBuildControllerInvocationArgs distBuildControllerInvocationArgs,
      ClientStatsTracker clientStatsTracker,
      boolean waitGracefullyForDistributedBuildThreadToFinish,
      long distributedBuildThreadKillTimeoutSeconds,
      Optional<String> autoStampedeMessage,
      RemoteBuildRuleSynchronizer remoteBuildRuleSynchronizer) {
    this.localBuildExecutorInvoker = localBuildExecutorInvoker;
    this.eventBus = eventBus;
    this.clientStatsTracker = clientStatsTracker;
    this.remoteBuildRuleSynchronizer = remoteBuildRuleSynchronizer;
    this.autoStampedeMessage = autoStampedeMessage;
    this.racerBuildRunner =
        createStampedeLocalBuildRunner(
            executorForLocalBuild, localBuildExecutorInvoker, "racer", false, Optional.empty());
    this.synchronizedBuildRunner =
        createStampedeLocalBuildRunner(
            executorForLocalBuild,
            localBuildExecutorInvoker,
            "synchronized",
            true,
            Optional.empty());
    this.distBuildRunner =
        createStampedeDistBuildExecutor(
            createDistBuildControllerInvoker(
                createDistBuildController(
                    remoteBuildRuleSynchronizer,
                    stampedeIdReference,
                    distBuildControllerArgsBuilder),
                distBuildControllerInvocationArgs),
            executorForDistBuildController,
            distBuildService,
            distBuildStartedEvent,
            waitGracefullyForDistributedBuildThreadToFinish,
            distributedBuildThreadKillTimeoutSeconds);
  }

  /**
   * Kicks off distributed build, then runs a multi-phase local build which.
   *
   * @throws InterruptedException if proceedToLocalSynchronizedBuildPhase gets interrupted.
   */
  public Optional<ExitCode> build(
      DistLocalBuildMode distLocalBuildMode, boolean localBuildFallbackEnabled)
      throws InterruptedException {
    LOG.info(
        String.format(
            "Stampede build client starting. distLocalBuildMode=[%s], localBuildFallbackEnabled=[%s].",
            distLocalBuildMode, localBuildFallbackEnabled));

    if (distLocalBuildMode.equals(DistLocalBuildMode.FIRE_AND_FORGET)) {
      distBuildRunner.runDistBuildSync();
      eventBus.post(ConsoleEvent.info("Fire and forget build was scheduled."));
      LOG.info("Stampede build in fire-and-forget mode started remotely. Exiting local client.");
      return Optional.of(ExitCode.SUCCESS);
    } else if (distLocalBuildMode.equals(DistLocalBuildMode.RULE_KEY_DIVERGENCE_CHECK)) {
      // Setup caching build engine so that ListenableFuture<ParallelRuleKeyCalculator<RuleKey>> is
      // populated
      localBuildExecutorInvoker.initLocalBuild(false, new NoOpRemoteBuildRuleCompletionWaiter());
      distBuildRunner.runDistBuildSync();
      return Optional.of(DistributedExitCode.convertToExitCode(distBuildRunner.getExitCode()));
    } else {
      // Kick off the distributed build
      distBuildRunner.runDistBuildAsync();
    }

    boolean proceedToLocalSynchronizedBuildPhase =
        distLocalBuildMode.equals(DistLocalBuildMode.WAIT_FOR_REMOTE);
    if (distLocalBuildMode.equals(DistLocalBuildMode.NO_WAIT_FOR_REMOTE)) {
      clientStatsTracker.setPerformedRacingBuild(true);
      proceedToLocalSynchronizedBuildPhase =
          !RacingBuildPhase.run(
              distBuildRunner,
              racerBuildRunner,
              remoteBuildRuleSynchronizer,
              localBuildFallbackEnabled,
              eventBus);
    }

    clientStatsTracker.setRacingBuildFinishedFirst(!proceedToLocalSynchronizedBuildPhase);

    if (proceedToLocalSynchronizedBuildPhase) {
      eventBus.post(new DistBuildSuperConsoleEvent(autoStampedeMessage));
      eventBus.post(BuildEvent.reset());
      SynchronizedBuildPhase.run(
          distBuildRunner,
          synchronizedBuildRunner,
          remoteBuildRuleSynchronizer,
          localBuildFallbackEnabled,
          eventBus);
    }

    distBuildRunner.printAnyFailures();

    Optional<ExitCode> localExitCode =
        synchronizedBuildRunner.isFinished()
            ? synchronizedBuildRunner.getExitCode()
            : racerBuildRunner.getExitCode();
    LOG.info(
        String.format(
            "All Stampede local builds finished. Final local exit code [%d]",
            localExitCode.map(ExitCode::getCode).orElse(-1)));

    return localExitCode;
  }

  public DistributedExitCode getDistBuildExitCode() {
    return distBuildRunner.getExitCode();
  }

  public StampedeId getStampedeId() {
    return Objects.requireNonNull(stampedeIdReference.get());
  }

  /**
   * ****************************** Helpers to create dependencies ******************************
   */
  private LocalBuildRunner createStampedeLocalBuildRunner(
      ExecutorService executorForLocalBuild,
      LocalBuildExecutorInvoker localBuildExecutorInvoker,
      String localBuildType,
      boolean isDownloadHeavyBuild,
      Optional<CountDownLatch> waitForBuildCalledLatch) {
    Objects.requireNonNull(remoteBuildRuleSynchronizer);
    return new LocalBuildRunner(
        executorForLocalBuild,
        localBuildExecutorInvoker,
        localBuildType,
        isDownloadHeavyBuild,
        remoteBuildRuleSynchronizer,
        waitForBuildCalledLatch);
  }

  private DistBuildRunner createStampedeDistBuildExecutor(
      DistBuildControllerInvoker distBuildControllerInvoker,
      ExecutorService executorToRunDistBuildController,
      DistBuildService distBuildService,
      BuildEvent.DistBuildStarted distBuildStartedEvent,
      boolean waitGracefullyForDistributedBuildThreadToFinish,
      long distributedBuildThreadKillTimeoutSeconds) {
    Objects.requireNonNull(eventBus);
    Objects.requireNonNull(racerBuildRunner);
    Objects.requireNonNull(synchronizedBuildRunner);
    Objects.requireNonNull(remoteBuildRuleSynchronizer);
    Objects.requireNonNull(stampedeIdReference);

    ImmutableSet<CountDownLatch> buildPhaseLatches =
        ImmutableSet.of(
            racerBuildRunner.getBuildPhaseLatch(), synchronizedBuildRunner.getBuildPhaseLatch());

    return new DistBuildRunner(
        distBuildControllerInvoker,
        executorToRunDistBuildController,
        eventBus,
        distBuildService,
        stampedeIdReference,
        distBuildStartedEvent,
        remoteBuildRuleSynchronizer,
        buildPhaseLatches,
        waitGracefullyForDistributedBuildThreadToFinish,
        distributedBuildThreadKillTimeoutSeconds);
  }

  private static DistBuildControllerInvoker createDistBuildControllerInvoker(
      DistBuildController distBuildController,
      DistBuildControllerInvocationArgs distBuildControllerInvocationArgs) {
    return () -> {
      StampedeExecutionResult distBuildResult =
          distBuildController.executeAndPrintFailuresToEventBus(
              distBuildControllerInvocationArgs.getExecutorService(),
              distBuildControllerInvocationArgs.getProjectFilesystem(),
              distBuildControllerInvocationArgs.getFileHashCache(),
              distBuildControllerInvocationArgs.getInvocationInfo(),
              distBuildControllerInvocationArgs.getBuildMode(),
              distBuildControllerInvocationArgs.getDistLocalBuildMode(),
              distBuildControllerInvocationArgs.getMinionRequirements(),
              distBuildControllerInvocationArgs.getRepository(),
              distBuildControllerInvocationArgs.getTenantId(),
              distBuildControllerInvocationArgs.getRuleKeyCalculatorFuture());

      return distBuildResult;
    };
  }

  private static DistBuildController createDistBuildController(
      RemoteBuildRuleSynchronizer remoteBuildRuleSynchronizer,
      AtomicReference<StampedeId> stampedeIdReference,
      DistBuildControllerArgs.Builder distBuildControllerArgsBuilder) {
    return new DistBuildController(
        distBuildControllerArgsBuilder
            .setRemoteBuildRuleCompletionNotifier(remoteBuildRuleSynchronizer)
            .setStampedeIdReference(stampedeIdReference)
            .build());
  }

  private StampedeId createPendingStampedeId() {
    StampedeId stampedeId = new StampedeId();
    stampedeId.setId(PENDING_STAMPEDE_ID);
    return stampedeId;
  }
}
