/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.jvm.java.abi;

import com.facebook.buck.jvm.java.abi.source.api.BootClasspathOracle;
import com.facebook.buck.jvm.java.plugin.PluginLoader;
import com.facebook.buck.util.ClassLoaderCache;
import com.facebook.buck.util.HumanReadableException;

import java.lang.reflect.Constructor;

import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;

public final class SourceBasedAbiStubber {
  public static Object newValidatingTaskListener(
      ClassLoaderCache classLoaderCache,
      JavaCompiler.CompilationTask task,
      BootClasspathOracle bootClasspathOracle,
      Diagnostic.Kind messageKind) {
    try {
      final ClassLoader pluginClassLoader =
          PluginLoader.getPluginClassLoader(classLoaderCache, task);
      final Class<?> validatingTaskListenerClass = Class.forName(
          "com.facebook.buck.jvm.java.abi.source.ValidatingTaskListener",
          false,
          pluginClassLoader);
      final Constructor<?> constructor =
          validatingTaskListenerClass.getConstructor(
              JavaCompiler.CompilationTask.class,
              BootClasspathOracle.class,
              Diagnostic.Kind.class);

      return constructor.newInstance(task, bootClasspathOracle, messageKind);
    } catch (ReflectiveOperationException e) {
      throw new HumanReadableException(
          e,
          "Could not load source-generated ABI validator. Your compiler might not support this. " +
              "If it doesn't, you may need to disable source-based ABI generation.");
    }
  }

  private SourceBasedAbiStubber() {

  }
}
