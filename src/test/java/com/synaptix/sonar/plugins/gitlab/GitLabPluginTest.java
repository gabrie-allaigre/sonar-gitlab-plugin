/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2016 Talanlabs
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package com.synaptix.sonar.plugins.gitlab;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GitLabPluginTest {

  @Test
  public void uselessTest() {
    assertThat(new GitLabPlugin().getExtensions().size()).isGreaterThan(1);
  }

}
