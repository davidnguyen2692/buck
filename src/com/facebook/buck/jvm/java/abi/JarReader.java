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

package com.facebook.buck.jvm.java.abi;

import com.google.common.collect.Iterables;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A {@link LibraryReader} that reads from a jar file.
 */
class JarReader implements LibraryReader<InputStream> {
  private final Path jarPath;
  @Nullable
  FileSystem fileSystem;
  @Nullable
  private DirectoryReader inner;

  JarReader(Path jarPath) {
    this.jarPath = jarPath;
  }

  @Override
  public List<Path> getRelativePaths() throws IOException {
    return getInner().getRelativePaths();
  }

  @Override
  public InputStream openResourceFile(Path relativePath) throws IOException {
    return getInner().openResourceFile(relativePath);
  }

  @Override
  public InputStream openClass(Path relativePath) throws IOException {
    return getInner().openClass(relativePath);
  }

  @Override
  public void close() throws IOException {
    if (fileSystem != null) {
      fileSystem.close();
      fileSystem = null;
      inner = null;
    }
  }

  private DirectoryReader getInner() throws IOException {
    if (inner == null) {
      fileSystem = FileSystems.newFileSystem(jarPath, null);
      inner = new DirectoryReader(
          Iterables.getOnlyElement(
              fileSystem.getRootDirectories()));
    }

    return inner;
  }
}
