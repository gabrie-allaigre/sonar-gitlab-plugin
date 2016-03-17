/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2016-2016 Talanlabs
 * gabriel.allaigre@talanlabs.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.synaptix.sonar.plugins.gitlab;

import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.resources.Project;

/**
 * This is a temporary solution before being able to use new postjob API in SQ 5.2.
 */
public class InputFileCacheSensor implements Sensor {

  private final GitLabPluginConfiguration gitLabPluginConfiguration;
  private final FileSystem fs;
  private final InputFileCache inputFileCache;

  public InputFileCacheSensor(GitLabPluginConfiguration gitLabPluginConfiguration, FileSystem fs, InputFileCache inputFileCache) {
    this.gitLabPluginConfiguration = gitLabPluginConfiguration;
    this.fs = fs;
    this.inputFileCache = inputFileCache;
  }

  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return gitLabPluginConfiguration.isEnabled();
  }

  @Override
  public void analyse(Project module, SensorContext context) {
    for (InputFile inputFile : fs.inputFiles(fs.predicates().all())) {
      inputFileCache.put(context.getResource(inputFile).getEffectiveKey(), inputFile);
    }
  }

  @Override
  public String toString() {
    return "GitLab Plugin InputFile Cache";
  }

}
