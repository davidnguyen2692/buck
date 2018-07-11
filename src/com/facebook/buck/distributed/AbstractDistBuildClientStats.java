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
package com.facebook.buck.distributed;

import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import java.util.Optional;
import java.util.OptionalInt;
import org.immutables.value.Value;

@Value.Immutable
@BuckStyleImmutable
abstract class AbstractDistBuildClientStats {
  abstract String stampedeId();

  abstract boolean buckClientError();

  abstract String userOrInferredBuildLabel();

  abstract String minionType();

  abstract Optional<String> buckClientErrorMessage();

  abstract boolean performedLocalBuild();

  abstract boolean performedRacingBuild();

  abstract Optional<Boolean> racingBuildFinishedFirst();

  abstract Optional<Boolean> isLocalFallbackBuildEnabled();

  abstract OptionalInt distributedBuildExitCode();

  abstract OptionalInt localBuildExitCode();

  abstract Optional<Long> postDistBuildLocalStepsDurationMs();

  abstract Optional<Long> localBuildDurationMs();

  abstract Optional<Long> localPreparationDurationMs();

  abstract Optional<Long> localGraphConstructionDurationMs();

  abstract Optional<Long> localFileHashComputationDurationMs();

  abstract Optional<Long> localTargetGraphSerializationDurationMs();

  abstract Optional<Long> localUploadFromDirCacheDurationMs();

  abstract Optional<Long> missingRulesUploadedFromDirCacheCount();

  abstract Optional<Long> performDistributedBuildDurationMs();

  abstract Optional<Long> createDistributedBuildDurationMs();

  abstract Optional<Long> uploadMissingFilesDurationMs();

  abstract Optional<Long> uploadTargetGraphDurationMs();

  abstract Optional<Long> uploadBuckDotFilesDurationMs();

  abstract Optional<Long> setBuckVersionDurationMs();

  abstract Optional<Long> missingFilesUploadedCount();

  abstract Optional<Long> materializeSlaveLogsDurationMs();

  abstract Optional<Long> publishBuildSlaveFinishedStatsDurationMs();

  abstract Optional<Long> postBuildAnalysisDurationMs();
}
