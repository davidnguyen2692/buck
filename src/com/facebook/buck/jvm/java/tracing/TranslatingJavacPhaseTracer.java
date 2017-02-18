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

package com.facebook.buck.jvm.java.tracing;

import com.facebook.buck.jvm.java.JavacEventSink;
import com.facebook.buck.jvm.java.plugin.PluginLoader;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.util.ClassLoaderCache;

import java.lang.reflect.Method;
import java.util.List;

import javax.annotation.Nullable;
import javax.tools.JavaCompiler;

/**
 * A {@link JavacPhaseTracer} that translates the trace data to be more useful.
 * <p>
 * The phases of compilation are described
 * <a href="http://openjdk.java.net/groups/compiler/doc/compilation-overview/index.html">here</a>.
 * The doc describes annotation processing as conceptually occuring before compilation, but
 * actually occurring somewhat out-of-phase with the conceptual model.
 * <p>
 * Javac calls {@link TracingTaskListener} according to the conceptual model described in that
 * document: annotation processing starts at the very beginning of the run, and ends after the
 * last annotation processor is run in the last round of processing. Then there is one last parse
 * and enter before going into analyze and generate. This is problematic from a performance
 * perspective, because some of the work attributed to annotation processing would have happened
 * regardless.
 * <p>
 * This class translates the tracing data from the conceptual model back into something that more
 * closely matches the actual implementation:
 * <ul>
 * <li>Parse, enter, analyze, and generate phases pass thru unchanged</li>
 * <li>What javac traces as an annotation processing round is renamed
 * "run annotation processors"</li>
 * <li>Annotation processing rounds are traced from the beginning of "run annotation processors"
 * to the beginning of the next "run annotation processors" or (for the last round)
 * the first analyze phase</li>
 * <li>Annotation processing is traced from the beginning of the first round to
 * the end of the last</li>
 * <li>If compilation ends during annotation processing (as can happen with -proc:only), it
 * detects this (via being closed by its caller) and emits appropriate tracing</li>
 * </ul>
 * In this way, the time attributed to annotation processing is always time
 * that would not have been spent if annotation processors were not present.
 */
public class TranslatingJavacPhaseTracer implements JavacPhaseTracer, AutoCloseable {
  private static final Logger LOG = Logger.get(TranslatingJavacPhaseTracer.class);
  private final JavacPhaseEventLogger logger;

  private boolean isProcessingAnnotations = false;
  private int roundNumber = 0;

  /**
   * @param next a TaskListener that should be notified of events outside of the trace windows. It
   *             is passed as Object because TaskListener is not available in Buck's ClassLoader
   */
  @Nullable
  public static TranslatingJavacPhaseTracer setupTracing(
      BuildTarget invokingTarget,
      ClassLoaderCache classLoaderCache,
      JavacEventSink eventSink,
      JavaCompiler.CompilationTask task,
      Object next) {
    try {
      final ClassLoader tracingTaskListenerClassLoader =
          PluginLoader.getPluginClassLoader(classLoaderCache, task);
      final Class<?> tracingTaskListenerClass = Class.forName(
          "com.facebook.buck.jvm.java.tracing.TracingTaskListener",
          false,
          tracingTaskListenerClassLoader);
      final Method setupTracingMethod = tracingTaskListenerClass.getMethod(
          "setupTracing",
          JavaCompiler.CompilationTask.class,
          JavacPhaseTracer.class,
          Object.class);
      final TranslatingJavacPhaseTracer tracer = new TranslatingJavacPhaseTracer(
          new JavacPhaseEventLogger(invokingTarget, eventSink));
      setupTracingMethod.invoke(
          null,
          task,
          tracer,
          next);

      return tracer;
    } catch (ReflectiveOperationException e) {
      LOG.warn(
          "Failed loading TracingTaskListener (%s: %s). " +
              "Perhaps using a compiler that doesn't support com.sun.source.util.JavaTask?",
          e.getClass().getSimpleName(), e.getMessage());
      return null;
    }
  }

  public TranslatingJavacPhaseTracer(JavacPhaseEventLogger logger) {
    this.logger = logger;
  }

  @Override
  public void beginParse(String filename) {
    logger.beginParse(filename);
  }

  @Override
  public void endParse() {
    logger.endParse();
  }

  @Override
  public void beginEnter() {
    logger.beginEnter();
  }

  @Override
  public void endEnter(List<String> filenames) {
    logger.endEnter(filenames);
  }

  @Override
  public void beginAnnotationProcessingRound() {
    if (isProcessingAnnotations) {
      logger.endAnnotationProcessingRound(false);
    } else {
      logger.beginAnnotationProcessing();
    }

    isProcessingAnnotations = true;
    roundNumber += 1;
    logger.beginAnnotationProcessingRound(roundNumber);
    logger.beginRunAnnotationProcessors();
  }

  @Override
  public void endAnnotationProcessingRound() {
    logger.endRunAnnotationProcessors();
  }

  @Override
  public void beginAnalyze(String filename, String typename) {
    if (isProcessingAnnotations) {
      logger.endAnnotationProcessingRound(true);
      logger.endAnnotationProcessing();
      isProcessingAnnotations = false;
    }

    logger.beginAnalyze(filename, typename);
  }

  @Override
  public void endAnalyze() {
    logger.endAnalyze();
  }

  @Override
  public void beginGenerate(String filename, String typename) {
    logger.beginGenerate(filename, typename);
  }

  @Override
  public void endGenerate() {
    logger.endGenerate();
  }

  @Override
  public void close() {
    if (isProcessingAnnotations) {
      // If javac is invoked with -proc:only, the last thing we'll hear from it is the end of
      // the annotation processing round. We won't get a beginAnalyze (or even a beginEnter) after
      // the annotation processors run for the last time.
      logger.endAnnotationProcessingRound(true);
      logger.endAnnotationProcessing();
      isProcessingAnnotations = false;
    }
  }
}
