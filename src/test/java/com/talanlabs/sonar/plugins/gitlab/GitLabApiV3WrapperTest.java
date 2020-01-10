/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2016-2017 Talanlabs
 * gabriel.allaigre@gmail.com
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

import com.talanlabs.gitlab.api.v3.GitLabAPI;
import com.talanlabs.gitlab.api.v3.models.projects.GitLabProject;
import com.talanlabs.gitlab.api.v3.services.GitLabAPICommits;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class GitLabApiV3WrapperTest {

    private GitLabPluginConfiguration gitLabPluginConfiguration;
    private SonarFacade sonarFacade;

    @Before
    public void before() {
        gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        sonarFacade = mock(SonarFacade.class);
        when(sonarFacade.getDashboardUrl()).thenReturn("http://sonardashboard");
    }

    @Test
    public void testGetGitLabUrl() {
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("abc123"));

        GitLabApiV3Wrapper facade = new GitLabApiV3Wrapper(gitLabPluginConfiguration, sonarFacade);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getWebUrl()).thenReturn("https://gitLab.com/gaby/test");
        facade.setGitLabProject(gitLabProject);

        assertThat(facade.getGitLabUrl(null, "src/main/Foo.java", 10)).isEqualTo("https://gitLab.com/gaby/test/blob/abc123/src/main/Foo.java#L10");
    }

    @Test
    public void testStatusSuccess() throws IOException {
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("1"));
        when(gitLabPluginConfiguration.refName()).thenReturn("master");

        GitLabApiV3Wrapper facade = new GitLabApiV3Wrapper(gitLabPluginConfiguration, sonarFacade);

        GitLabAPI gitLabAPI = mock(GitLabAPI.class);
        facade.setGitLabAPI(gitLabAPI);

        GitLabAPICommits gitLabAPICommits = mock(GitLabAPICommits.class);
        when(gitLabAPICommits.postCommitStatus(1, "1", "pending", "master", "sonarqube", "http://sonardashboard", "")).thenReturn(null);

        when(gitLabAPI.getGitLabAPICommits()).thenReturn(gitLabAPICommits);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getId()).thenReturn(1);
        facade.setGitLabProject(gitLabProject);

        facade.createOrUpdateSonarQubeStatus("pending", "nothing");

        verify(gitLabAPICommits).postCommitStatus(1, "1", "pending", "master", "sonarqube", "http://sonardashboard", "nothing");
    }

    @Test
    public void testStatusFailed() throws IOException {
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("1"));
        when(gitLabPluginConfiguration.refName()).thenReturn("master");

        GitLabApiV3Wrapper facade = new GitLabApiV3Wrapper(gitLabPluginConfiguration, sonarFacade);

        GitLabAPI gitLabAPI = mock(GitLabAPI.class);
        facade.setGitLabAPI(gitLabAPI);

        GitLabAPICommits gitLabAPICommits = mock(GitLabAPICommits.class);
        when(gitLabAPICommits.postCommitStatus(1, "1", "pending", "master", "sonarqube", "http://sonardashboard", "nothing")).thenThrow(new IOException());

        when(gitLabAPI.getGitLabAPICommits()).thenReturn(gitLabAPICommits);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getId()).thenReturn(1);
        facade.setGitLabProject(gitLabProject);

        facade.createOrUpdateSonarQubeStatus("pending", "nothing");
    }

    @Test
    public void testGlobalComment() throws IOException {
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("1"));
        when(gitLabPluginConfiguration.refName()).thenReturn("master");

        GitLabApiV3Wrapper facade = new GitLabApiV3Wrapper(gitLabPluginConfiguration, sonarFacade);

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
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("1"));
        when(gitLabPluginConfiguration.refName()).thenReturn("master");

        GitLabApiV3Wrapper facade = new GitLabApiV3Wrapper(gitLabPluginConfiguration, sonarFacade);

        GitLabAPI gitLabAPI = mock(GitLabAPI.class);
        facade.setGitLabAPI(gitLabAPI);

        GitLabAPICommits gitLabAPICommits = mock(GitLabAPICommits.class);
        when(gitLabAPICommits.postCommitComments("1", "1", "pending", "master", null, null)).thenReturn(null);

        when(gitLabAPI.getGitLabAPICommits()).thenReturn(gitLabAPICommits);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getId()).thenReturn(1);
        facade.setGitLabProject(gitLabProject);

        facade.createOrUpdateReviewComment(null, "src/main/Foo.java", 5, "nothing");

        verify(gitLabAPICommits).postCommitComments(1, "1", "nothing", "src/main/Foo.java", 5, "new");
    }
}