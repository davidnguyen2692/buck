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

package com.facebook.buck.cxx;

import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.rules.ConstantToolProvider;
import com.facebook.buck.rules.HashedFileTool;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Utility class to create a C/C++ platform described in the "cxx"
 * section of .buckconfig, with reasonable system defaults.
 */
public class DefaultCxxPlatforms {

  // Utility class, do not instantiate.
  private DefaultCxxPlatforms() { }

  public static final Flavor FLAVOR = ImmutableFlavor.of("default");

  private static final Path DEFAULT_C_FRONTEND = Paths.get("/usr/bin/gcc");
  private static final Path DEFAULT_CXX_FRONTEND = Paths.get("/usr/bin/g++");
  private static final Path DEFAULT_AR = Paths.get("/usr/bin/ar");
  private static final Path DEFAULT_STRIP = Paths.get("/usr/bin/strip");
  private static final Path DEFAULT_RANLIB = Paths.get("/usr/bin/ranlib");
  private static final Path DEFAULT_NM = Paths.get("/usr/bin/nm");

  private static final Path DEFAULT_OSX_C_FRONTEND = Paths.get("/usr/bin/clang");
  private static final Path DEFAULT_OSX_CXX_FRONTEND = Paths.get("/usr/bin/clang++");

  public static CxxPlatform build(
      Platform platform,
      ProjectFilesystem filesystem,
      CxxBuckConfig config) {
    String sharedLibraryExtension;
    String sharedLibraryVersionedExtensionFormat;
    String staticLibraryExtension;
    String objectFileExtension;
    Path defaultCFrontend;
    Path defaultCxxFrontend;
    LinkerProvider.Type linkerType;
    Archiver archiver;
    DebugPathSanitizer compilerSanitizer;
    Optional<String> binaryExtension;
    ImmutableMap<String, String> env = config.getEnvironment();
    switch (platform) {
      case LINUX:
        sharedLibraryExtension = "so";
        sharedLibraryVersionedExtensionFormat = "so.%s";
        staticLibraryExtension = "a";
        objectFileExtension = "o";
        defaultCFrontend = getExecutablePath("gcc", DEFAULT_C_FRONTEND, env);
        defaultCxxFrontend = getExecutablePath("g++", DEFAULT_CXX_FRONTEND, env);
        linkerType = LinkerProvider.Type.GNU;
        archiver = new GnuArchiver(new HashedFileTool(getExecutablePath("ar", DEFAULT_AR, env)));
        compilerSanitizer = new PrefixMapDebugPathSanitizer(
            config.getDebugPathSanitizerLimit(),
            File.separatorChar,
            Paths.get("."),
            ImmutableBiMap.of(),
            filesystem.getRootPath().toAbsolutePath(),
            CxxToolProvider.Type.GCC,
            filesystem);
        binaryExtension = Optional.empty();
        break;
      case MACOS:
        sharedLibraryExtension = "dylib";
        sharedLibraryVersionedExtensionFormat = ".%s.dylib";
        staticLibraryExtension = "a";
        objectFileExtension = "o";
        defaultCFrontend = getExecutablePath("clang", DEFAULT_OSX_C_FRONTEND, env);
        defaultCxxFrontend = getExecutablePath("clang++", DEFAULT_OSX_CXX_FRONTEND, env);
        linkerType = LinkerProvider.Type.DARWIN;
        archiver = new BsdArchiver(new HashedFileTool(getExecutablePath("ar", DEFAULT_AR, env)));
        compilerSanitizer = new PrefixMapDebugPathSanitizer(
            config.getDebugPathSanitizerLimit(),
            File.separatorChar,
            Paths.get("."),
            ImmutableBiMap.of(),
            filesystem.getRootPath().toAbsolutePath(),
            CxxToolProvider.Type.CLANG,
            filesystem);
        binaryExtension = Optional.empty();
        break;
      case WINDOWS:
        sharedLibraryExtension = "dll";
        sharedLibraryVersionedExtensionFormat = "dll";
        staticLibraryExtension = "lib";
        objectFileExtension = "obj";
        defaultCFrontend = getExecutablePath("gcc", DEFAULT_C_FRONTEND, env);
        defaultCxxFrontend = getExecutablePath("g++", DEFAULT_CXX_FRONTEND, env);
        linkerType = LinkerProvider.Type.WINDOWS;
        archiver = new WindowsArchiver(new HashedFileTool(
            getExecutablePath("ar", DEFAULT_AR, env)));
        compilerSanitizer = new PrefixMapDebugPathSanitizer(
            config.getDebugPathSanitizerLimit(),
            File.separatorChar,
            Paths.get("."),
            ImmutableBiMap.of(),
            filesystem.getRootPath().toAbsolutePath(),
            CxxToolProvider.Type.GCC,
            filesystem);
        binaryExtension = Optional.of("exe");
        break;
      case FREEBSD:
        sharedLibraryExtension = "so";
        sharedLibraryVersionedExtensionFormat = "so.%s";
        staticLibraryExtension = "a";
        objectFileExtension = "o";
        defaultCFrontend = getExecutablePath("gcc", DEFAULT_C_FRONTEND, env);
        defaultCxxFrontend = getExecutablePath("g++", DEFAULT_CXX_FRONTEND, env);
        linkerType = LinkerProvider.Type.GNU;
        archiver = new BsdArchiver(new HashedFileTool(getExecutablePath("ar", DEFAULT_AR, env)));
        compilerSanitizer = new PrefixMapDebugPathSanitizer(
            config.getDebugPathSanitizerLimit(),
            File.separatorChar,
            Paths.get("."),
            ImmutableBiMap.of(),
            filesystem.getRootPath().toAbsolutePath(),
            CxxToolProvider.Type.GCC,
            filesystem);
        binaryExtension = Optional.empty();
        break;
      //$CASES-OMITTED$
      default:
        throw new RuntimeException(String.format("Unsupported platform: %s", platform));
    }

    PreprocessorProvider aspp =
        new PreprocessorProvider(
            defaultCFrontend,
            Optional.empty());
    CompilerProvider as =
        new CompilerProvider(
            defaultCFrontend,
            Optional.empty());

    PreprocessorProvider cpp =
        new PreprocessorProvider(
            defaultCFrontend,
            Optional.empty());
    CompilerProvider cc =
        new CompilerProvider(
            defaultCFrontend,
            Optional.empty());
    PreprocessorProvider cxxpp =
        new PreprocessorProvider(
            defaultCxxFrontend,
            Optional.empty());
    CompilerProvider cxx =
        new CompilerProvider(
            defaultCxxFrontend,
            Optional.empty());

    return CxxPlatforms.build(
        FLAVOR,
        platform,
        config,
        as,
        aspp,
        cc,
        cxx,
        cpp,
        cxxpp,
        new DefaultLinkerProvider(
            linkerType,
            new ConstantToolProvider(new HashedFileTool(defaultCxxFrontend))),
        ImmutableList.of(),
        new HashedFileTool(getExecutablePath("strip", DEFAULT_STRIP, env)),
        archiver,
        new HashedFileTool(getExecutablePath("ranlib", DEFAULT_RANLIB, env)),
        new PosixNmSymbolNameTool(new HashedFileTool(getExecutablePath("nm", DEFAULT_NM, env))),
        ImmutableList.of(),
        ImmutableList.of(),
        ImmutableList.of(),
        ImmutableList.of(),
        sharedLibraryExtension,
        sharedLibraryVersionedExtensionFormat,
        staticLibraryExtension,
        objectFileExtension,
        compilerSanitizer,
        new MungingDebugPathSanitizer(
            config.getDebugPathSanitizerLimit(),
            File.separatorChar,
            Paths.get("."),
            ImmutableBiMap.of()),
        ImmutableMap.of(),
        binaryExtension,
        config.getHeaderVerification());
  }

  private static Path getExecutablePath(
      String executableName,
      Path unresolvedLocation,
      ImmutableMap<String, String> env) {
    return new ExecutableFinder()
        .getOptionalExecutable(Paths.get(executableName), env)
        .orElse(unresolvedLocation);
  }
}
