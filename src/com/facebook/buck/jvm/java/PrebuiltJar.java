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

package com.facebook.buck.jvm.java;

import static com.facebook.buck.rules.BuildableProperties.Kind.LIBRARY;

import com.facebook.buck.android.AndroidPackageable;
import com.facebook.buck.android.AndroidPackageableCollector;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.io.MorePaths;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildOutputInitializer;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.BuildableProperties;
import com.facebook.buck.rules.ExportDependencies;
import com.facebook.buck.rules.InitializableFromDisk;
import com.facebook.buck.rules.OnDiskBuildInfo;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.keys.SupportsInputBasedRuleKey;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.HashCode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@BuildsAnnotationProcessor
public class PrebuiltJar extends AbstractBuildRule
    implements AndroidPackageable, ExportDependencies, HasClasspathEntries,
    InitializableFromDisk<JavaLibrary.Data>, JavaLibrary, SupportsInputBasedRuleKey {

  private static final BuildableProperties OUTPUT_TYPE = new BuildableProperties(LIBRARY);

  @AddToRuleKey
  private final SourcePath binaryJar;
  private final BuildTarget abiJar;
  private final Path copiedBinaryJar;
  @AddToRuleKey
  private final Optional<SourcePath> sourceJar;
  @SuppressWarnings("PMD.UnusedPrivateField")
  @AddToRuleKey
  private final Optional<SourcePath> gwtJar;
  @AddToRuleKey
  private final Optional<String> javadocUrl;
  @AddToRuleKey
  private final Optional<String> mavenCoords;
  @AddToRuleKey
  private final boolean provided;
  private final Path internalAbiJar;
  private final Supplier<ImmutableSet<Path>> transitiveClasspathsSupplier;
  private final Supplier<ImmutableSet<JavaLibrary>> transitiveClasspathDepsSupplier;

  private final BuildOutputInitializer<Data> buildOutputInitializer;

  public PrebuiltJar(
      BuildRuleParams params,
      SourcePathResolver resolver,
      SourcePath binaryJar,
      BuildTarget abiJar,
      Optional<SourcePath> sourceJar,
      Optional<SourcePath> gwtJar,
      Optional<String> javadocUrl,
      Optional<String> mavenCoords,
      final boolean provided) {
    super(params, resolver);
    this.binaryJar = binaryJar;
    this.abiJar = abiJar;
    this.sourceJar = sourceJar;
    this.gwtJar = gwtJar;
    this.javadocUrl = javadocUrl;
    this.mavenCoords = mavenCoords;
    this.provided = provided;

    this.internalAbiJar =
        BuildTargets.getGenPath(getProjectFilesystem(), getBuildTarget(), "%s-abi.jar");

    transitiveClasspathsSupplier =
        Suppliers.memoize(() -> JavaLibraryClasspathProvider.getClasspathsFromLibraries(
            getTransitiveClasspathDeps()));

    this.transitiveClasspathDepsSupplier =
        Suppliers.memoize(
            () -> {
              if (provided) {
                return JavaLibraryClasspathProvider.getClasspathDeps(
                    PrebuiltJar.this.getDeclaredDeps());
              }
              return ImmutableSet.<JavaLibrary>builder()
                  .add(PrebuiltJar.this)
                  .addAll(JavaLibraryClasspathProvider.getClasspathDeps(
                      PrebuiltJar.this.getDeclaredDeps()))
                  .build();
            });

    Path fileName = resolver.getRelativePath(binaryJar).getFileName();
    String fileNameWithJarExtension =
        String.format("%s.jar", MorePaths.getNameWithoutExtension(fileName));
    copiedBinaryJar = BuildTargets.getGenPath(
        getProjectFilesystem(),
        getBuildTarget(),
        "__%s__/" + fileNameWithJarExtension);

    buildOutputInitializer =
        new BuildOutputInitializer<>(params.getBuildTarget(), this);
  }

  @Override
  public BuildableProperties getProperties() {
    return OUTPUT_TYPE;
  }

  public Optional<SourcePath> getSourceJar() {
    return sourceJar;
  }

  public Optional<String> getJavadocUrl() {
    return javadocUrl;
  }

  @Override
  public Optional<BuildTarget> getAbiJar() {
    return Optional.of(abiJar);
  }

  @Override
  public ImmutableSortedMap<String, HashCode> getClassNamesToHashes() {
    return buildOutputInitializer.getBuildOutput().getClassNamesToHashes();
  }

  @Override
  public JavaLibrary.Data initializeFromDisk(OnDiskBuildInfo onDiskBuildInfo) throws IOException {
    return JavaLibraryRules.initializeFromDisk(
        getBuildTarget(),
        getProjectFilesystem(),
        onDiskBuildInfo);
  }

  @Override
  public BuildOutputInitializer<Data> getBuildOutputInitializer() {
    return buildOutputInitializer;
  }

  @Override
  public ImmutableSortedSet<BuildRule> getDepsForTransitiveClasspathEntries() {
    return getDeps();
  }

  @Override
  public ImmutableSet<Path> getTransitiveClasspaths() {
    return transitiveClasspathsSupplier.get();
  }

  @Override
  public ImmutableSet<JavaLibrary> getTransitiveClasspathDeps() {
    return transitiveClasspathDepsSupplier.get();
  }

  @Override
  public ImmutableSet<Path> getImmediateClasspaths() {
    if (!provided) {
      return ImmutableSet.of(getResolver().getAbsolutePath(getBinaryJar()));
    } else {
      return ImmutableSet.of();
    }
  }

  @Override
  public ImmutableSet<Path> getOutputClasspaths() {
    return ImmutableSet.of(getResolver().getAbsolutePath(getBinaryJar()));
  }

  @Override
  public ImmutableSortedSet<Path> getJavaSrcs() {
    return ImmutableSortedSet.of();
  }

  @Override
  public ImmutableSortedSet<SourcePath> getSources() {
    return ImmutableSortedSet.of();
  }

  @Override
  public ImmutableSortedSet<SourcePath> getResources() {
    return ImmutableSortedSet.of();
  }

  @Override
  public ImmutableSortedSet<BuildRule> getExportedDeps() {
    return getDeclaredDeps();
  }

  @Override
  public Optional<Path> getGeneratedSourcePath() {
    return Optional.empty();
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      final BuildableContext buildableContext) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    // Create a copy of the JAR in case it was generated by another rule.
    Path resolvedBinaryJar = getResolver().getAbsolutePath(binaryJar);
    Path resolvedCopiedBinaryJar = getProjectFilesystem().resolve(copiedBinaryJar);
    Preconditions.checkState(
        !resolvedBinaryJar.equals(resolvedCopiedBinaryJar),
        "%s: source (%s) can't be equal to destination (%s) when copying prebuilt JAR.",
        getBuildTarget().getFullyQualifiedName(),
        resolvedBinaryJar,
        copiedBinaryJar);

    if (getResolver().getFilesystem(binaryJar).isDirectory(resolvedBinaryJar)) {
      steps.add(new MakeCleanDirectoryStep(getProjectFilesystem(), copiedBinaryJar));
      steps.add(CopyStep.forDirectory(
          getProjectFilesystem(),
          resolvedBinaryJar,
          copiedBinaryJar,
          CopyStep.DirectoryMode.CONTENTS_ONLY));
    } else {
      if (!MorePaths.getFileExtension(copiedBinaryJar.getFileName())
          .equals(MorePaths.getFileExtension(resolvedBinaryJar))) {
        context.getEventBus().post(
            ConsoleEvent.warning("Assuming %s is a JAR and renaming to %s in %s. " +
                "Change the extension of the binary_jar to '.jar' to remove this warning.",
                resolvedBinaryJar.getFileName(),
                copiedBinaryJar.getFileName(),
                getBuildTarget().getFullyQualifiedName()));
      }

      steps.add(new MkdirStep(getProjectFilesystem(), copiedBinaryJar.getParent()));
      steps.add(CopyStep.forFile(getProjectFilesystem(), resolvedBinaryJar, copiedBinaryJar));
    }
    buildableContext.recordArtifact(copiedBinaryJar);

    // Create a step to compute the ABI key.
    steps.add(new MkdirStep(getProjectFilesystem(), internalAbiJar.getParent()));
    steps.add(new RmStep(getProjectFilesystem(), internalAbiJar, true));
    steps.add(
        new CalculateAbiStep(
            buildableContext,
            getProjectFilesystem(),
            resolvedBinaryJar,
            internalAbiJar));

    JavaLibraryRules.addAccumulateClassNamesStep(this, buildableContext, steps);

    return steps.build();
  }

  @Override
  public Iterable<AndroidPackageable> getRequiredPackageables() {
    return AndroidPackageableCollector.getPackageableRules(getDeclaredDeps());
  }

  @Override
  public void addToCollector(AndroidPackageableCollector collector) {
    if (!provided) {
      collector.addClasspathEntry(this, getBinaryJar());
      collector.addPathToThirdPartyJar(getBuildTarget(), getBinaryJar());
    }
  }

  @Override
  public Path getPathToOutput() {
    return copiedBinaryJar;
  }

  public SourcePath getBinaryJar() {
    return new BuildTargetSourcePath(getBuildTarget());
  }

  @Override
  public Optional<String> getMavenCoords() {
    return mavenCoords;
  }
}
