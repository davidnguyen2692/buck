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

import com.facebook.buck.io.HashingDeterministicJarWriter;
import com.facebook.buck.io.MorePaths;
import com.facebook.buck.io.ProjectFilesystem;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;

/**
 * A {@link StubJarWriter} that writes to a file through {@link ProjectFilesystem}.
 */
class FilesystemStubJarWriter implements StubJarWriter {
  private final HashingDeterministicJarWriter jar;
  private boolean closed = false;

  public FilesystemStubJarWriter(
      ProjectFilesystem filesystem,
      Path outputPath) throws IOException {
    Preconditions.checkState(
        !filesystem.exists(outputPath),
        "Output file already exists: %s)",
        outputPath);

    if (outputPath.getParent() != null && !filesystem.exists(outputPath.getParent())) {
      filesystem.createParentDirs(outputPath);
    }

    jar = new HashingDeterministicJarWriter(
        new JarOutputStream(filesystem.newFileOutputStream(outputPath)));
  }

  @Override
  public void writeResource(Path relativePath, InputStream resourceContents) throws IOException {
    jar.writeEntry(MorePaths.pathWithUnixSeparators(relativePath), resourceContents);
  }

  @Override
  public void writeClass(Path relativePath, ClassMirror stub) throws IOException {
    try (InputStream contents = stub.getStubClassBytes().openStream()) {
      jar.writeEntry(MorePaths.pathWithUnixSeparators(relativePath), contents);
    }
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      jar.close();
    }
    closed = true;
  }
}
