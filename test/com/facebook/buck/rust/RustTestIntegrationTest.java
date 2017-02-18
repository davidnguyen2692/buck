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

package com.facebook.buck.rust;

import static org.junit.Assert.assertThat;

import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.testutil.integration.TestDataHelper;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;

public class RustTestIntegrationTest {
  @Rule
  public TemporaryPaths tmp = new TemporaryPaths();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void ensureRustIsAvailable() throws IOException, InterruptedException {
    RustAssumptions.assumeRustCompilerAvailable();
  }


  @Test
  public void simpleTestFailure() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "binary_with_tests", tmp);
    workspace.setUp();

    assertThat(
        workspace.runBuckCommand("test", "//:test_failure").getStderr(),
        Matchers.containsString("assertion failed: false"));
  }

  @Test
  public void simpleTestSuccess() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "binary_with_tests", tmp);
    workspace.setUp();

    workspace.runBuckCommand("test", "//:test_success").assertSuccess();
  }

  @Test
  public void simpleTestIgnore() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "binary_with_tests", tmp);
    workspace.setUp();

    workspace.runBuckCommand("test", "//:test_ignore").assertSuccess();
  }

  @Test
  public void testManyModules() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "binary_with_tests", tmp);
    workspace.setUp();

    workspace.runBuckCommand("test", "//:test_many_modules").assertTestFailure();
  }

  @Test
  public void testSuccessFailure() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "binary_with_tests", tmp);
    workspace.setUp();

    workspace.runBuckCommand("test", "//:success_failure").assertTestFailure();
  }

  @Test
  public void runnableTest() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "binary_with_tests", tmp);
    workspace.setUp();

    assertThat(
        workspace.runBuckCommand("run", "//:test_success").assertSuccess().getStdout(),
        Matchers.containsString("test test_hello_world ... ok"));
  }

  @Test
  public void testWithCrateRoot() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "binary_with_tests", tmp);
    workspace.setUp();

    workspace.runBuckCommand("test", "//:with_crate_root").assertSuccess();
  }

}
