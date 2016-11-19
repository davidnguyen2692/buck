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

package com.facebook.buck.apple.xcode.xcodeproj;

import com.facebook.buck.apple.xcode.XcodeprojSerializer;

/**
 * Target backed by shell scripts or nothing (only specifying dependencies).
 */
public class PBXLegacyTarget extends PBXTarget {
    private String buildWorkingDirectory;
    private String buildToolPath;
    private String buildArgumentsString;
    private boolean passBuildSettingsInEnvironment;

  public PBXLegacyTarget(String name) {
    super(name);
    this.buildWorkingDirectory = new String();
    this.buildToolPath = new String();
    this.buildArgumentsString = "$(ACTION)";
    this.passBuildSettingsInEnvironment = true;
  }

  @Override
  public String isa() {
    return "PBXLegacyTarget";
  }
  
  public String getBuildWorkingDirectory() {
    return buildWorkingDirectory;
  }

  public void setBuildWorkingDirectory(String buildWorkingDirectory) {
    this.buildWorkingDirectory = buildWorkingDirectory;
  }
  
  public String getBuildToolPath() {
    return buildToolPath;
  }

  public void setBuildToolPath(String buildToolPath) {
    this.buildToolPath = buildToolPath;
  }
  
  public String getBuildArgumentsString() {
    return buildArgumentsString;
  }

  public void setBuildArgumentsString(String buildArgumentsString) {
    this.buildArgumentsString = buildArgumentsString;
  }
  
  public boolean getPassBuildSettingsInEnvironment() {
    return passBuildSettingsInEnvironment;
  }

  public void setPassBuildSettingsInEnvironment(boolean passBuildSettingsInEnvironment) {
    this.passBuildSettingsInEnvironment = passBuildSettingsInEnvironment;
  }
  
  @Override
  public void serializeInto(XcodeprojSerializer s) {
    super.serializeInto(s);
    
    s.addField("buildWorkingDirectory", buildWorkingDirectory);
    s.addField("buildToolPath", buildToolPath);
    s.addField("buildArgumentsString", buildArgumentsString);
    s.addField("passBuildSettingsInEnvironment", passBuildSettingsInEnvironment);
  }
}
