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

import com.talanlabs.gitlab.api.GitLabAPI;
import com.talanlabs.gitlab.api.models.projects.GitLabProject;
import com.talanlabs.gitlab.api.services.GitLabAPICommits;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.fs.internal.DefaultInputFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class CommitFacadeTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testGetGitLabUrl() throws Exception {
        File gitBasedir = temp.newFolder();

        GitLabPluginConfiguration gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("abc123"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        facade.setGitBaseDir(gitBasedir);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getWebUrl()).thenReturn("https://gitLab.com/gaby/test");
        facade.setGitLabProject(gitLabProject);

        InputPath inputPath = mock(InputPath.class);
        when(inputPath.file()).thenReturn(new File(gitBasedir, "src/main/Foo.java"));
        assertThat(facade.getGitLabUrl(null, inputPath, 10)).isEqualTo("https://gitLab.com/gaby/test/blob/abc123/src/main/Foo.java#L10");
    }

    @Test
    public void testPatchLineMapping_some_deleted_lines() throws IOException {
        CommitFacade facade = new CommitFacade(mock(GitLabPluginConfiguration.class));
        Set<CommitFacade.Line> patchLocationMapping = facade.getPositionsFromPatch(
                "@@ -17,9 +17,6 @@\n  * along with this program; if not, write to the Free Software Foundation,\n  * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.\n  */\n-/**\n- * Deprecated in 4.5.1. JFreechart charts are replaced by Javascript charts.\n- */\n @ParametersAreNonnullByDefault\n package org.sonar.plugins.core.charts;\n ");

        assertThat(patchLocationMapping).isEmpty();
    }

    @Test
    public void testPatchLineMapping_some_added_lines() throws IOException {
        CommitFacade facade = new CommitFacade(mock(GitLabPluginConfiguration.class));
        Set<CommitFacade.Line> patchLocationMapping =
                facade.getPositionsFromPatch(
                        "@@ -24,9 +24,9 @@\n /**\n  * A plugin is a group of extensions. See <code>org.sonar.api.Extension</code> interface to browse\n  * available extension points.\n- * <p/>\n  * <p>The manifest property <code>Plugin-Class</code> must declare the name of the implementation class.\n  * It is automatically set by sonar-packaging-maven-plugin when building plugins.</p>\n+ * <p>Implementation must declare a public constructor with no-parameters.</p>\n  *\n  * @see org.sonar.api.Extension\n  * @since 1.10");

        System.out.println(patchLocationMapping);
        assertThat(patchLocationMapping).containsOnly(new CommitFacade.Line(29, " * <p>Implementation must declare a public constructor with no-parameters.</p>"));
    }

    @Test
    public void testPatchLineMapping_LF_and_CRLF_as_newline_characters() throws IOException {
        CommitFacade facade = new CommitFacade(mock(GitLabPluginConfiguration.class));
        Set<CommitFacade.Line> patchLocationMapping =
                facade.getPositionsFromPatch(
                        "@@ -24,9 +24,9 @@\n /**\r\n  * A plugin is a group of extensions. See <code>org.sonar.api.Extension</code> interface to browse\n  * available extension points.\r\n- * <p/>\r\n  * <p>The manifest property <code>Plugin-Class</code> must declare the name of the implementation class.\n  * It is automatically set by sonar-packaging-maven-plugin when building plugins.</p>\r\n+ * <p>Implementation must declare a public constructor with no-parameters.</p>\r\n  *\r\n  * @see org.sonar.api.Extension\r\n  * @since 1.10");

        System.out.println(patchLocationMapping);
        assertThat(patchLocationMapping).containsOnly(new CommitFacade.Line(29, " * <p>Implementation must declare a public constructor with no-parameters.</p>"));
    }

    @Test
    public void testPatchLineMapping_no_newline_at_the_end() throws IOException {
        CommitFacade facade = new CommitFacade(mock(GitLabPluginConfiguration.class));
        Set<CommitFacade.Line> patchLocationMapping =
                facade.getPositionsFromPatch("@@ -1 +0,0 @@\n-<fake/>\n\\ No newline at end of file");

        assertThat(patchLocationMapping).isEmpty();
    }

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
    public void testStatusSuccess() throws IOException {
        GitLabPluginConfiguration gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("1"));
        when(gitLabPluginConfiguration.refName()).thenReturn("master");

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);

        GitLabAPI gitLabAPI = mock(GitLabAPI.class);
        facade.setGitLabAPI(gitLabAPI);

        GitLabAPICommits gitLabAPICommits = mock(GitLabAPICommits.class);
        when(gitLabAPICommits.postCommitStatus(1, "1", "pending", "master", "sonarqube", "server", "")).thenReturn(null);

        when(gitLabAPI.getGitLabAPICommits()).thenReturn(gitLabAPICommits);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getId()).thenReturn(1);
        facade.setGitLabProject(gitLabProject);

        facade.createOrUpdateSonarQubeStatus("pending", "nothing");

        verify(gitLabAPICommits).postCommitStatus(1, "1", "pending", "master", "sonarqube", null, "nothing");
    }

    @Test
    public void testStatusFailed() throws IOException {
        GitLabPluginConfiguration gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("1"));
        when(gitLabPluginConfiguration.refName()).thenReturn("master");

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);

        GitLabAPI gitLabAPI = mock(GitLabAPI.class);
        facade.setGitLabAPI(gitLabAPI);

        GitLabAPICommits gitLabAPICommits = mock(GitLabAPICommits.class);
        when(gitLabAPICommits.postCommitStatus(1, "1", "pending", "master", "sonarqube", null, "nothing")).thenThrow(new IOException());

        when(gitLabAPI.getGitLabAPICommits()).thenReturn(gitLabAPICommits);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getId()).thenReturn(1);
        facade.setGitLabProject(gitLabProject);

        facade.createOrUpdateSonarQubeStatus("pending", "nothing");
    }

    @Test
    public void testGlobalComment() throws IOException {
        GitLabPluginConfiguration gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("1"));
        when(gitLabPluginConfiguration.refName()).thenReturn("master");

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);

        GitLabAPI gitLabAPI = mock(GitLabAPI.class);
        facade.setGitLabAPI(gitLabAPI);

        GitLabAPICommits gitLabAPICommits = mock(GitLabAPICommits.class);
        when(gitLabAPICommits.postCommitComments("1", "1", "pending", "master", null, null)).thenReturn(null);

        when(gitLabAPI.getGitLabAPICommits()).thenReturn(gitLabAPICommits);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getId()).thenReturn(1);
        facade.setGitLabProject(gitLabProject);

        facade.addGlobalComment("nothing");

        verify(gitLabAPICommits).postCommitComments(1, "1", "nothing", null, null, null);
    }

    @Test
    public void testReviewComment() throws IOException {
        GitLabPluginConfiguration gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("1"));
        when(gitLabPluginConfiguration.refName()).thenReturn("master");

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);

        File gitBasedir = temp.newFolder();
        facade.setGitBaseDir(gitBasedir);

        GitLabAPI gitLabAPI = mock(GitLabAPI.class);
        facade.setGitLabAPI(gitLabAPI);

        GitLabAPICommits gitLabAPICommits = mock(GitLabAPICommits.class);
        when(gitLabAPICommits.postCommitComments("1", "1", "pending", "master", null, null)).thenReturn(null);

        when(gitLabAPI.getGitLabAPICommits()).thenReturn(gitLabAPICommits);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getId()).thenReturn(1);
        facade.setGitLabProject(gitLabProject);

        InputFile inputFile = mock(InputFile.class);
        when(inputFile.file()).thenReturn(new File(gitBasedir, "src/main/Foo.java"));

        facade.createOrUpdateReviewComment(null, inputFile, 5, "nothing");

        verify(gitLabAPICommits).postCommitComments(1, "1", "nothing", "src/main/Foo.java", 5, "new");
    }

    @Test
    public void tesGetPath() throws IOException {
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
}