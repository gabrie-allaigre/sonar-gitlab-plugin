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

import com.google.common.collect.Sets;
import com.talanlabs.gitlab.api.GitLabAPI;
import com.talanlabs.gitlab.api.models.commits.GitLabCommitDiff;
import com.talanlabs.gitlab.api.models.projects.GitLabProject;
import com.talanlabs.gitlab.api.services.GitLabAPICommits;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.fs.internal.DefaultInputFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
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
        when(gitLabPluginConfiguration.commitSHA()).thenReturn("abc123");

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        facade.setGitBaseDir(gitBasedir);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getWebUrl()).thenReturn("https://gitLab.com/gaby/test");
        facade.setGitLabProject(gitLabProject);

        InputPath inputPath = mock(InputPath.class);
        when(inputPath.file()).thenReturn(new File(gitBasedir, "src/main/Foo.java"));
        assertThat(facade.getGitLabUrl(inputPath, 10)).isEqualTo("https://gitLab.com/gaby/test/blob/abc123/src/main/Foo.java#L10");
    }

    @Test
    public void testPatchLineMapping_some_deleted_lines() throws IOException {
        Set<Integer> patchLocationMapping = new HashSet<>();
        CommitFacade.processPatch(patchLocationMapping,
                "@@ -17,9 +17,6 @@\n  * along with this program; if not, write to the Free Software Foundation,\n  * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.\n  */\n-/**\n- * Deprecated in 4.5.1. JFreechart charts are replaced by Javascript charts.\n- */\n @ParametersAreNonnullByDefault\n package org.sonar.plugins.core.charts;\n ");

        assertThat(patchLocationMapping).containsOnly(17, 18, 19, 20, 21, 22);
    }

    @Test
    public void testPatchLineMapping_some_added_lines() throws IOException {
        Set<Integer> patchLocationMapping = new HashSet<>();
        CommitFacade.processPatch(patchLocationMapping,
                "@@ -24,9 +24,9 @@\n /**\n  * A plugin is a group of extensions. See <code>org.sonar.api.Extension</code> interface to browse\n  * available extension points.\n- * <p/>\n  * <p>The manifest property <code>Plugin-Class</code> must declare the name of the implementation class.\n  * It is automatically set by sonar-packaging-maven-plugin when building plugins.</p>\n+ * <p>Implementation must declare a public constructor with no-parameters.</p>\n  *\n  * @see org.sonar.api.Extension\n  * @since 1.10");

        System.out.println(patchLocationMapping);
        assertThat(patchLocationMapping).containsOnly(32, 24, 25, 26, 27, 28, 29, 30, 31);
    }

    @Test
    public void testPatchLineMapping_no_newline_at_the_end() throws IOException {
        Set<Integer> patchLocationMapping = new HashSet<>();
        CommitFacade.processPatch(patchLocationMapping, "@@ -1 +0,0 @@\n-<fake/>\n\\ No newline at end of file");

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
        when(gitLabPluginConfiguration.commitSHA()).thenReturn("1");
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
        when(gitLabPluginConfiguration.commitSHA()).thenReturn("1");
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
        when(gitLabPluginConfiguration.commitSHA()).thenReturn("1");
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
        when(gitLabPluginConfiguration.commitSHA()).thenReturn("1");
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

        facade.createOrUpdateReviewComment(inputFile, 5, "nothing");

        verify(gitLabAPICommits).postCommitComments(1, "1", "nothing", "src/main/Foo.java", 5, "new");
    }

    @Test
    public void testMapPatchPositionsToLines() throws IOException {
        GitLabCommitDiff diff1 = new GitLabCommitDiff();
        diff1.setNewPath("src/main/Foo.java");
        diff1.setDiff(
                "@@ -24,9 +24,9 @@\n /**\n  * A plugin is a group of extensions. See <code>org.sonar.api.Extension</code> interface to browse\n  * available extension points.\n- * <p/>\n  * <p>The manifest property <code>Plugin-Class</code> must declare the name of the implementation class.\n  * It is automatically set by sonar-packaging-maven-plugin when building plugins.</p>\n+ * <p>Implementation must declare a public constructor with no-parameters.</p>\n  *\n  * @see org.sonar.api.Extension\n  * @since 1.10");
        GitLabCommitDiff diff2 = new GitLabCommitDiff();
        diff2.setNewPath("src/main/Foo2.java");
        diff2.setDiff(
                "@@ -2,11 +2,9 @@\n /**\n  * A plugin is a group of extensions. See <code>org.sonar.api.Extension</code> interface to browse\n  * available extension points.\n- * <p/>\n  * <p>The manifest property <code>Plugin-Class</code> must declare the name of the implementation class.\n  * It is automatically set by sonar-packaging-maven-plugin when building plugins.</p>\n+ * <p>Implementation must declare a public constructor with no-parameters.</p>\n  *\n  * @see org.sonar.api.Extension\n  * @since 1.10");

        assertThat(CommitFacade.mapPatchPositionsToLines(Arrays.asList(diff1, diff2))).containsEntry("src/main/Foo.java", Sets.newHashSet(32, 24, 25, 26, 27, 28, 29, 30, 31))
                .containsEntry("src/main/Foo2.java", Sets.newHashSet(2, 3, 4, 5, 6, 7, 8, 9, 10));
    }


}
