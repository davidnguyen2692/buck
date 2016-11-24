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

package com.facebook.buck.util.cache;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.io.WatchEvents;
import com.facebook.buck.log.Logger;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Optional;

public class WatchedFileHashCache extends DefaultFileHashCache {

  private static final Logger LOG = Logger.get(WatchedFileHashCache.class);

  public WatchedFileHashCache(ProjectFilesystem projectFilesystem) {
    super(projectFilesystem, Optional.empty());
  }

  /**
   * Called when file change events are posted to the file change EventBus to invalidate cached
   * build rules if required. {@link Path}s contained within events must all be relative to the
   * {@link ProjectFilesystem} root.
   */
  @Subscribe
  public synchronized void onFileSystemChange(WatchEvent<?> event) {
    if (WatchEvents.isPathChangeEvent(event)) {
      // Path event, remove the path from the cache as it has been changed, added or deleted.
      final Path path = ((Path) event.context()).normalize();
      LOG.verbose("Invalidating %s", path);
      Iterable<Path> pathsToInvalidate =
          Maps.filterEntries(
              loadingCache.asMap(),
              entry -> {
                Preconditions.checkNotNull(entry);

                // If we get a invalidation for a file which is a prefix of our current one, this
                // means the invalidation is of a symlink which points to a directory (since events
                // won't be triggered for directories).  We don't fully support symlinks, however,
                // we do support some limited flows that use them to point to read-only storage
                // (e.g. the `project.read_only_paths`).  For these limited flows to work correctly,
                // we invalidate.
                if (entry.getKey().startsWith(path)) {
                  return true;
                }

                // Otherwise, we want to invalidate the entry if the path matches it.  We also
                // invalidate any directories that contain this entry, so use the following
                // comparison to capture both these scenarios.
                if (path.startsWith(entry.getKey())) {
                  return true;
                }

                return false;
              }
          ).keySet();
      LOG.verbose("Paths to invalidate: %s", pathsToInvalidate);
      for (Path pathToInvalidate : pathsToInvalidate) {
        invalidateResolved(pathToInvalidate);
      }
    } else {
      // Non-path change event, likely an overflow due to many change events: invalidate everything.
      LOG.debug("Invalidating all");
      invalidateAll();
    }
  }

}
