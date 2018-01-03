/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2016-2017 Talanlabs
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
package com.talanlabs.sonar.plugins.gitlab;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommitFacadeTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testInitGitBaseDirNotFound() throws Exception {
        CommitFacade facade = new CommitFacade(mock(GitLabPluginConfiguration.class));
        File projectBaseDir = temp.newFolder();
        facade.initGitBaseDir(projectBaseDir);
        assertThat(facade.getPath(new DefaultInputFile("foo", "src/main/java/Foo.java").setModuleBaseDir(projectBaseDir.toPath()))).isEqualTo("src/main/java/Foo.java");
    }

    @Test
    public void testInitGitBaseDir() throws Exception {
        CommitFacade facade = new CommitFacade(mock(GitLabPluginConfiguration.class));
        File gitBaseDir = temp.newFolder();
        Files.createDirectory(gitBaseDir.toPath().resolve(".git"));
        File projectBaseDir = new File(gitBaseDir, "myProject");
        facade.initGitBaseDir(projectBaseDir);
        assertThat(facade.getPath(new DefaultInputFile("foo", "src/main/java/Foo.java").setModuleBaseDir(projectBaseDir.toPath()))).isEqualTo("myProject/src/main/java/Foo.java");
    }

    @Test
    public void testGetPath() throws IOException {
        GitLabPluginConfiguration gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("1"));
        when(gitLabPluginConfiguration.refName()).thenReturn("master");

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);

        File gitBasedir = temp.newFolder();
        facade.setGitBaseDir(gitBasedir);

        InputFile inputFile = mock(InputFile.class);
        when(inputFile.file()).thenReturn(new File(gitBasedir, "src/main/Foo.java"));
        Assertions.assertThat(facade.getPath(inputFile)).isEqualTo("src/main/Foo.java");

        when(gitLabPluginConfiguration.prefixDirectory()).thenReturn("toto/");

        Assertions.assertThat(facade.getPath(inputFile)).isEqualTo("toto/src/main/Foo.java");
    }

    @Test
    public void testWriteSast() throws IOException {
        CommitFacade facade = new CommitFacade(mock(GitLabPluginConfiguration.class));
        File projectBaseDir = temp.newFolder();
        facade.initGitBaseDir(projectBaseDir);

        facade.writeSastFile("[{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"Issue\",\"file\":\"file\",\"line\":\"0\",\"priority\":\"INFO\",\"solution\":\"http://myserver\"}]");

        File file = new File(projectBaseDir, "gl-sast-report.json");
        Assertions.assertThat(file).exists().hasContent("[{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"Issue\",\"file\":\"file\",\"line\":\"0\",\"priority\":\"INFO\",\"solution\":\"http://myserver\"}]");
    }
}