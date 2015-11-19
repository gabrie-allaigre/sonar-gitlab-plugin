/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2015 Synaptix-Labs
 * contact@synaptix-labs.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package com.synaptix.sonar.plugins.gitlab;

import org.sonar.api.BatchComponent;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.fs.InputFile;

import javax.annotation.CheckForNull;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a temporary solution before being able to use new postjob API in SQ 5.2.
 */
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class InputFileCache implements BatchComponent {

  private final Map<String, InputFile> inputFileByKey = new HashMap<>();

  void put(String componentKey, InputFile inputFile) {
    inputFileByKey.put(componentKey, inputFile);
  }

  @CheckForNull
  public InputFile byKey(String componentKey) {
    return inputFileByKey.get(componentKey);
  }

  @Override
  public String toString() {
    return "GitLab Plugin InputFile Cache";
  }

}
