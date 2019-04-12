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

package com.facebook.buck.features.rust;

import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.toolchain.tool.impl.HashedFileTool;
import com.facebook.buck.core.toolchain.toolprovider.ToolProvider;
import com.facebook.buck.core.toolchain.toolprovider.impl.ConstantToolProvider;
import com.facebook.buck.core.util.immutables.BuckStyleTuple;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.linker.DefaultLinkerProvider;
import com.facebook.buck.cxx.toolchain.linker.LinkerProvider;
import com.facebook.buck.io.ExecutableFinder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.immutables.value.Value;

/** Factory class for creating {@link RustPlatform}s from {@link BuckConfig} sections. */
@Value.Immutable
@BuckStyleTuple
abstract class AbstractRustPlatformFactory {

  private static final Path DEFAULT_RUSTC_COMPILER = Paths.get("rustc");

  abstract BuckConfig getBuckConfig();

  abstract ExecutableFinder getExecutableFinder();

  /** @return a {@link RustPlatform} from the given config subsection name. */
  public RustPlatform getPlatform(String name, CxxPlatform cxxPlatform) {
    RustBuckConfig rustBuckConfig = new RustBuckConfig(getBuckConfig());
    Optional<ToolProvider> linker = rustBuckConfig.getRustLinker(name);
    return RustPlatform.builder()
        .setRustCompiler(
            rustBuckConfig
                .getRustCompiler(name)
                .orElseGet(
                    () -> {
                      HashedFileTool tool =
                          new HashedFileTool(
                              () ->
                                  getBuckConfig()
                                      .getPathSourcePath(
                                          getExecutableFinder()
                                              .getExecutable(
                                                  DEFAULT_RUSTC_COMPILER,
                                                  getBuckConfig().getEnvironment())));
                      return new ConstantToolProvider(tool);
                    }))
        .addAllRustLibraryFlags(rustBuckConfig.getRustcLibraryFlags(name))
        .addAllRustBinaryFlags(rustBuckConfig.getRustcBinaryFlags(name))
        .addAllRustTestFlags(rustBuckConfig.getRustcTestFlags(name))
        .addAllRustCheckFlags(rustBuckConfig.getRustcCheckFlags(name))
        .setLinker(linker)
        .setLinkerProvider(
            linker
                .map(
                    tp ->
                        (LinkerProvider)
                            new DefaultLinkerProvider(
                                rustBuckConfig
                                    .getLinkerPlatform(name)
                                    .orElse(cxxPlatform.getLd().getType()),
                                tp,
                                true))
                .orElseGet(cxxPlatform::getLd))
        .addAllLinkerArgs(rustBuckConfig.getLinkerFlags(name))
        .addAllLinkerArgs(!linker.isPresent() ? cxxPlatform.getLdflags() : ImmutableList.of())
        .setCxxPlatform(cxxPlatform)
        .build();
  }
}
