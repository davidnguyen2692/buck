/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.util.concurrent;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class is like {@link ThreadPoolExecutor}, but tries to keep the total active work below the
 * specific limits.  Regardless of the configured limitations, however, we always allow at least one
 * work item to execute so that we can continue to make progress.
 */
public class LimitedThreadPoolExecutor extends ThreadPoolExecutor {

  private static final OperatingSystemMXBean mbean =
      ManagementFactory.getOperatingSystemMXBean();

  /**
   * Don't start additional jobs while the system load average is greater than this number.
   */
  private final double loadLimit;

  /**
   * Track the number of jobs we're currently executing.  We can't use
   * {@link ThreadPoolExecutor#getActiveCount} because getActiveCount includes the jobs blocked in
   * {@link #waitForLimitation}.
   */
  private final AtomicInteger currentlyExecuting = new AtomicInteger(0);

  public LimitedThreadPoolExecutor(
      ThreadFactory threadFactory,
      BlockingQueue<Runnable> workQueue,
      int threadCount,
      double loadLimit) {
    super(
        /* corePoolSize */ threadCount,
        /* maximumPoolSize */ threadCount,
        /* keepAliveTime */ 0L, TimeUnit.MILLISECONDS,
        /* workQueue */ workQueue,
        /* threadFactory */ threadFactory,
        /* handler */ new ThreadPoolExecutor.DiscardPolicy());
    this.loadLimit = loadLimit;
  }

  private void waitForLimitation() {
    for (;;) {
      if (currentlyExecuting.getAndIncrement() == 0) {
        break;
      }

      if (mbean.getSystemLoadAverage() < loadLimit) {
        break;
      }

      currentlyExecuting.getAndDecrement();

      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
      }
    }

  }

  @Override
  protected void beforeExecute(Thread t, Runnable r) {
    super.beforeExecute(t, r);
    waitForLimitation(); // Returns with currentlyExecuting incremented
  }

  @Override
  protected void afterExecute(Runnable r, Throwable t) {
    currentlyExecuting.getAndDecrement();
    super.afterExecute(r, t);
  }

}
