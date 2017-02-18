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

package com.facebook.buck.thrift;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.facebook.buck.zip.ZipCompressionLevel;
import com.facebook.buck.zip.ZipStep;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;

public class SrcZip extends AbstractBuildRule {

  @AddToRuleKey(stringify = true)
  private final Path sourceZip;
  @AddToRuleKey(stringify = true)
  private final Path sourceDirectory;

  public SrcZip(
      BuildRuleParams params,
      Path sourceZip,
      Path sourceDirectory) {
    super(params);
    this.sourceZip = sourceZip;
    this.sourceDirectory = sourceDirectory;
  }

  @Override
  public Path getPathToOutput() {
    return sourceZip;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {

    buildableContext.recordArtifact(sourceZip);

    ProjectFilesystem projectFilesystem = getProjectFilesystem();
    return ImmutableList.of(
        new RmStep(projectFilesystem, sourceZip),
        new MkdirStep(projectFilesystem, sourceZip.getParent()),
        new ZipStep(
            getProjectFilesystem(),
            sourceZip,
            /* paths */ ImmutableSet.of(),
            /* junkPaths */ false,
            ZipCompressionLevel.MIN_COMPRESSION_LEVEL,
            sourceDirectory));
  }

}
