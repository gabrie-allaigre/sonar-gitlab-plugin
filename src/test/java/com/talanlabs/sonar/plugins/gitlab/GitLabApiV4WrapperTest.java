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

import com.talanlabs.gitlab.api.Paged;
import com.talanlabs.gitlab.api.v4.GitLabAPI;
import com.talanlabs.gitlab.api.v4.GitlabMergeRequestDiff;
import com.talanlabs.gitlab.api.v4.models.projects.GitLabProject;
import com.talanlabs.gitlab.api.v4.services.GitLabAPICommits;
import com.talanlabs.gitlab.api.v4.services.GitLabAPIMergeRequestDiff;
import com.talanlabs.gitlab.api.v4.services.GitLabAPIMergeRequestDiscussion;
import org.junit.Test;
import org.mockito.Matchers;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.*;

public class GitLabApiV4WrapperTest {

    @Test
    public void testGetGitLabUrl() {
        GitLabPluginConfiguration gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("abc123"));

        GitLabApiV4Wrapper facade = new GitLabApiV4Wrapper(gitLabPluginConfiguration);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getWebUrl()).thenReturn("https://gitLab.com/gaby/test");
        facade.setGitLabProject(gitLabProject);

        assertThat(facade.getGitLabUrl(null, "src/main/Foo.java", 10)).isEqualTo("https://gitLab.com/gaby/test/blob/abc123/src/main/Foo.java#L10");
    }

    @Test
    public void testStatusSuccess() throws IOException {
        GitLabPluginConfiguration gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("1"));
        when(gitLabPluginConfiguration.refName()).thenReturn("master");

        GitLabApiV4Wrapper facade = new GitLabApiV4Wrapper(gitLabPluginConfiguration);

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

        GitLabApiV4Wrapper facade = new GitLabApiV4Wrapper(gitLabPluginConfiguration);

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

        GitLabApiV4Wrapper facade = new GitLabApiV4Wrapper(gitLabPluginConfiguration);

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

        GitLabApiV4Wrapper facade = new GitLabApiV4Wrapper(gitLabPluginConfiguration);

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

    @Test
    public void testCreateReviewDiscussionMissingIidFail() {
        GitLabPluginConfiguration gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.mergeRequestIid()).thenReturn(-1);
        when(gitLabPluginConfiguration.isMergeRequestDiscussionEnabled()).thenReturn(true);

        GitLabApiV4Wrapper facade = new GitLabApiV4Wrapper(gitLabPluginConfiguration);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getId()).thenReturn(1);
        facade.setGitLabProject(gitLabProject);

        assertThatIllegalArgumentException().isThrownBy(() ->
                facade.createOrUpdateReviewComment(null, "src/main/Foo.java", 5, "nothing"));
    }

    @Test
    public void testCreateReviewDiscussionEmptyMergeRequestDiffsFail() throws Exception {
        Integer projectId = 1;
        Integer mrIid = 1;

        GitLabPluginConfiguration gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.mergeRequestIid()).thenReturn(mrIid);
        when(gitLabPluginConfiguration.isMergeRequestDiscussionEnabled()).thenReturn(true);

        GitLabApiV4Wrapper facade = new GitLabApiV4Wrapper(gitLabPluginConfiguration);

        GitLabAPI gitLabAPI = mock(GitLabAPI.class);
        facade.setGitLabAPI(gitLabAPI);

        Paged paged = mock(Paged.class);

        GitLabAPIMergeRequestDiff gitLabAPIMergeRequestDiff = mock(GitLabAPIMergeRequestDiff.class);
        when(gitLabAPIMergeRequestDiff.getMergeRequestDiff(projectId, mrIid)).thenReturn(paged);

        when(gitLabAPI.getGitLabAPIMergeRequestDiff()).thenReturn(gitLabAPIMergeRequestDiff);
        when(paged.getResults()).thenReturn(null);

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getId()).thenReturn(projectId);
        facade.setGitLabProject(gitLabProject);

        assertThatIllegalArgumentException().isThrownBy(() ->
                facade.createOrUpdateReviewComment(null, "src/main/Foo.java", 5, "nothing"));

        when(paged.getResults()).thenReturn(Collections.emptyList());

        assertThatIllegalArgumentException().isThrownBy(() ->
                facade.createOrUpdateReviewComment(null, "src/main/Foo.java", 5, "nothing"));
    }

    @Test
    public void testCreateReviewDiscussionMissing() throws Exception {
        Integer projectId = 1;
        Integer mrIid = 1;

        GitLabPluginConfiguration gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.mergeRequestIid()).thenReturn(mrIid);
        when(gitLabPluginConfiguration.isMergeRequestDiscussionEnabled()).thenReturn(true);

        GitLabApiV4Wrapper facade = new GitLabApiV4Wrapper(gitLabPluginConfiguration);

        GitLabAPI gitLabAPI = mock(GitLabAPI.class);
        facade.setGitLabAPI(gitLabAPI);

        Paged paged = mock(Paged.class);

        GitLabAPIMergeRequestDiff gitLabAPIMergeRequestDiff = mock(GitLabAPIMergeRequestDiff.class);
        when(gitLabAPIMergeRequestDiff.getMergeRequestDiff(projectId, mrIid)).thenReturn(paged);

        GitlabMergeRequestDiff mergeRequestDiff = gitlabMergeRequestDiff(mrIid);

        when(gitLabAPI.getGitLabAPIMergeRequestDiff()).thenReturn(gitLabAPIMergeRequestDiff);
        when(paged.getResults()).thenReturn(Collections.singletonList(mergeRequestDiff));

        GitLabProject gitLabProject = mock(GitLabProject.class);
        when(gitLabProject.getId()).thenReturn(projectId);
        facade.setGitLabProject(gitLabProject);

        GitLabAPIMergeRequestDiscussion mergeRequestDiscussion = mock(GitLabAPIMergeRequestDiscussion.class);
        when(gitLabAPI.getGitLabAPIMergeRequestDiscussion()).thenReturn(mergeRequestDiscussion);

        facade.createOrUpdateReviewComment(null, "src/main/Foo.java", 5, "nothing");

        verify(mergeRequestDiscussion).createDiscussion(Matchers.eq(projectId), Matchers.eq(mrIid), anyObject());
    }

    private GitlabMergeRequestDiff gitlabMergeRequestDiff(int mrIid) {
        String randomCommitSha = UUID.randomUUID().toString();
        GitlabMergeRequestDiff gitlabMergeRequestDiff = new GitlabMergeRequestDiff();
        gitlabMergeRequestDiff.setId(1);
        gitlabMergeRequestDiff.setBaseCommitSha(randomCommitSha);
        gitlabMergeRequestDiff.setCreatedAt(Date.from(Instant.now()));
        gitlabMergeRequestDiff.setHeadCommitSha(randomCommitSha);
        gitlabMergeRequestDiff.setMergeRequestId(mrIid);
        gitlabMergeRequestDiff.setRealSize(10);
        gitlabMergeRequestDiff.setStartCommitSha(randomCommitSha);
        gitlabMergeRequestDiff.setState("SUCCESS");
        return gitlabMergeRequestDiff;
    }
}