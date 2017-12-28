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
import org.junit.Before;
import org.junit.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.batch.postjob.internal.DefaultPostJobDescriptor;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.System2;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

public class CommitIssuePostJobTest {

    private Settings settings;
    private CommitIssuePostJob commitIssuePostJob;
    private CommitFacade commitFacade;
    private PostJobContext context;

    @Before
    public void prepare() throws Exception {
        commitFacade = Mockito.mock(CommitFacade.class);
        settings = new Settings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL").description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE).build()).addComponents(GitLabPlugin.definitions()));
        settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "abc123");

        GitLabPluginConfiguration config = new GitLabPluginConfiguration(settings, new System2());
        context = Mockito.mock(PostJobContext.class);

        commitIssuePostJob = new CommitIssuePostJob(config, commitFacade, new MarkDownUtils(settings));
    }

    @Test
    public void testCommitAnalysisNoIssue1() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, false);

        Mockito.when(context.issues()).thenReturn(Collections.emptyList());
        commitIssuePostJob.execute(context);
        Mockito.verify(commitFacade, Mockito.never()).addGlobalComment(null);
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("success", "SonarQube reported no issues");
    }

    @Test
    public void testCommitAnalysisNoIssue2() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, true);

        Mockito.when(context.issues()).thenReturn(Arrays.asList());
        commitIssuePostJob.execute(context);
        Mockito.verify(commitFacade).addGlobalComment("SonarQube analysis reported no issues.");
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("success", "SonarQube reported no issues");
    }

    @Test
    public void testCommitAnalysisWithNewIssues() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, false);

        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        PostJobIssue lineNotVisible = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        DefaultInputFile inputFile2 = new DefaultInputFile("foo", "src/Foo2.php");
        PostJobIssue fileNotInPR = Utils.newMockedIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        PostJobIssue notNewIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        PostJobIssue issueOnDir = Utils.newMockedIssue("foo:src", Severity.BLOCKER, true, "msg4");

        PostJobIssue issueOnProject = Utils.newMockedIssue("foo", Severity.BLOCKER, true, "msg");

        PostJobIssue globalIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(context.issues()).thenReturn(Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        commitIssuePostJob.execute(context);
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("SonarQube analysis reported 6 issues"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("* :no_entry: 6 blocker"));
        Mockito.verify(commitFacade).addGlobalComment(AdditionalMatchers.not(Mockito.contains("1. [Project")));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("1. :no_entry: [msg2](http://gitlab/blob/abc123/src/Foo.php#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)"));

        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("failed", "SonarQube reported 6 issues, with 6 blocker (fail)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesOnly() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, true);

        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        PostJobIssue lineNotVisible = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        DefaultInputFile inputFile2 = new DefaultInputFile("foo", "src/Foo2.php");
        PostJobIssue fileNotInPR = Utils.newMockedIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        PostJobIssue notNewIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        PostJobIssue issueOnDir = Utils.newMockedIssue("foo:src", Severity.BLOCKER, true, "msg4");

        PostJobIssue issueOnProject = Utils.newMockedIssue("foo", Severity.BLOCKER, true, "msg");

        PostJobIssue globalIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(context.issues()).thenReturn(Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        commitIssuePostJob.execute(context);
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("SonarQube analysis reported 5 issues"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("* :no_entry: 5 blocker"));
        Mockito.verify(commitFacade).addGlobalComment(AdditionalMatchers.not(Mockito.contains("1. [Project")));
        Mockito.verify(commitFacade)
                .addGlobalComment(Mockito.contains("1. :no_entry: [msg2](http://gitlab/blob/abc123/src/Foo.php#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)"));

        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");

        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("failed", "SonarQube reported 5 issues, with 5 blocker (fail)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesOnlyLine() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_LINE, true);

        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        PostJobIssue lineNotVisible = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        DefaultInputFile inputFile2 = new DefaultInputFile("foo", "src/Foo2.php");
        PostJobIssue fileNotInPR = Utils.newMockedIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        PostJobIssue notNewIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        PostJobIssue issueOnDir = Utils.newMockedIssue("foo:src", Severity.BLOCKER, true, "msg4");

        PostJobIssue issueOnProject = Utils.newMockedIssue("foo", Severity.BLOCKER, true, "msg");

        PostJobIssue globalIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(context.issues()).thenReturn(Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        commitIssuePostJob.execute(context);
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("SonarQube analysis reported 1 issue"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("* :no_entry: 1 blocker"));
        Mockito.verify(commitFacade).addGlobalComment(AdditionalMatchers.not(Mockito.contains("1. [Project")));
        Mockito.verify(commitFacade)
                .addGlobalComment(AdditionalMatchers.not(Mockito.contains("1. :no_entry: [msg2](http://gitlab/blob/abc123/src/Foo.php#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)")));

        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");

        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("failed", "SonarQube reported 1 issue, with 1 blocker (fail)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesOnlyReview() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, true);

        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue1 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        PostJobIssue newIssue2 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        PostJobIssue newIssue3 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg3");
        Mockito.when(commitFacade.getGitLabUrl("def456", inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        Mockito.when(context.issues()).thenReturn(Arrays.asList(newIssue1, newIssue2, newIssue3));
        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 2)).thenReturn("abc123");

        commitIssuePostJob.execute(context);

        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1,
                ":no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n" + ":no_entry: msg2 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 2, ":no_entry: msg3 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
    }

    @Test
    public void testSortIssues() {
        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        DefaultInputFile inputFile2 = new DefaultInputFile("foo", "src/Foo2.php");

        // Blocker and 8th line => Should be displayed in 3rd position
        PostJobIssue newIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 8, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        // Blocker and 2nd line (Foo2.php) => Should be displayed in 4th position
        PostJobIssue issueInSecondFile = Utils.newMockedIssue("foo:src/Foo2.php", inputFile2, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        // Major => Should be displayed in 6th position
        PostJobIssue newIssue2 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 4, Severity.MAJOR, true, "msg3");

        // Critical => Should be displayed in 5th position
        PostJobIssue newIssue3 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 3, Severity.CRITICAL, true, "msg4");

        // Critical => Should be displayed in 7th position
        PostJobIssue newIssue4 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 13, Severity.INFO, true, "msg5");

        // Blocker on project => Should be displayed 1st position
        PostJobIssue issueOnProject = Utils.newMockedIssue("foo", Severity.BLOCKER, true, "msg6");

        // Blocker and no line => Should be displayed in 2nd position
        PostJobIssue globalIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg7");

        Mockito.when(context.issues()).thenReturn(Arrays.asList(newIssue, globalIssue, issueOnProject, newIssue4, newIssue2, issueInSecondFile, newIssue3));
        Mockito.when(commitFacade.hasFile(any(InputFile.class))).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(any(InputFile.class), anyInt())).thenReturn(null);

        commitIssuePostJob.execute(context);

        Mockito.verify(commitFacade).addGlobalComment(commentCaptor.capture());

        String comment = commentCaptor.getValue();
        Assertions.assertThat(comment).containsSequence("msg6", "msg7", "msg1", "msg2", "msg4", "msg3", "msg5");
    }

    @Test
    public void testCommitAnalysisWithNewCriticalIssues() {
        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.CRITICAL, true, "msg1");
        when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        when(context.issues()).thenReturn(Collections.singletonList(newIssue));
        when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn(null);

        commitIssuePostJob.execute(context);

        verify(commitFacade).createOrUpdateSonarQubeStatus("failed", "SonarQube reported 1 issue, with 1 critical (fail)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesNoBlockerNorCritical() {
        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.MAJOR, true, "msg1");
        when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        when(context.issues()).thenReturn(Collections.singletonList(newIssue));
        when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn(null);

        commitIssuePostJob.execute(context);

        verify(commitFacade).createOrUpdateSonarQubeStatus("success", "SonarQube reported 1 issue, with 1 major");
    }

    @Test
    public void testCommitAnalysisWithNewBlockerAndCriticalIssues() {
        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.CRITICAL, true, "msg1");
        when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        PostJobIssue lineNotVisible = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        when(context.issues()).thenReturn(Arrays.asList(newIssue, lineNotVisible));
        when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn(null);

        commitIssuePostJob.execute(context);

        verify(commitFacade).createOrUpdateSonarQubeStatus("failed", "SonarQube reported 2 issues, with 1 blocker (fail) and 1 critical (fail)");
    }

    @Test
    public void testUnexpectedException() {
        String innerMsg = "Failed to get issues";
        // not really realistic unexpected error, but good enough for this test
        when(context.issues()).thenThrow(new IllegalStateException(innerMsg));
        commitIssuePostJob.execute(context);

        String msg = "SonarQube failed to complete the review of this commit: " + innerMsg;
        verify(commitFacade).createOrUpdateSonarQubeStatus("failed", msg);
    }

    @Test
    public void testCommitAnalysisNoIssueExit() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, false);
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());

        Mockito.when(context.issues()).thenReturn(Collections.emptyList());
        commitIssuePostJob.execute(context);
        Mockito.verify(commitFacade, Mockito.never()).addGlobalComment(null);
        Mockito.verify(commitFacade, Mockito.never()).createOrUpdateSonarQubeStatus("success", "SonarQube reported no issues");
    }

    @Test
    public void testCommitAnalysisWithNewIssues2() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, false);
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_GLOBAL_COMMENT, true);

        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        PostJobIssue lineNotVisible = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        DefaultInputFile inputFile2 = new DefaultInputFile("foo", "src/Foo2.php");
        PostJobIssue fileNotInPR = Utils.newMockedIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        PostJobIssue notNewIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        PostJobIssue issueOnDir = Utils.newMockedIssue("foo:src", Severity.BLOCKER, true, "msg4");

        PostJobIssue issueOnProject = Utils.newMockedIssue("foo", Severity.BLOCKER, true, "msg");

        PostJobIssue globalIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(context.issues()).thenReturn(Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn(null);

        Assertions.assertThatThrownBy(() -> commitIssuePostJob.execute(context)).isInstanceOf(MessageException.class);

        Mockito.verify(commitFacade, Mockito.never()).addGlobalComment(Mockito.contains("SonarQube analysis reported 6 issues"));
        Mockito.verify(commitFacade, Mockito.never()).createOrUpdateSonarQubeStatus("failed", "SonarQube reported 6 issues, with 6 blocker");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesNoBlockerNorCritical2() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());

        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.MAJOR, true, "msg1");
        when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        when(context.issues()).thenReturn(Collections.singletonList(newIssue));
        when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn(null);

        commitIssuePostJob.execute(context);

        Mockito.verify(commitFacade, Mockito.never()).createOrUpdateSonarQubeStatus("success", "SonarQube reported 1 issue, no criticals or blockers");
    }

    @Test
    public void testDescriptor() {
        DefaultPostJobDescriptor postJobDescriptor = new DefaultPostJobDescriptor();
        commitIssuePostJob.describe(postJobDescriptor);

        Assertions.assertThat(postJobDescriptor.name()).isEqualTo("GitLab Commit Issue Publisher");
        Assertions.assertThat(postJobDescriptor.properties()).containsExactly(GitLabPlugin.GITLAB_URL, GitLabPlugin.GITLAB_USER_TOKEN, GitLabPlugin.GITLAB_PROJECT_ID, GitLabPlugin.GITLAB_COMMIT_SHA);
    }

    @Test
    public void testCommitAnalysisWithNewIssuesUniqueInline() {
        settings.setProperty(GitLabPlugin.GITLAB_UNIQUE_ISSUE_PER_INLINE, true);

        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue1 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        PostJobIssue newIssue2 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        Mockito.when(context.issues()).thenReturn(Arrays.asList(newIssue1, newIssue2));
        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        commitIssuePostJob.execute(context);

        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg2 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesExistsComment() {
        settings.setProperty(GitLabPlugin.GITLAB_UNIQUE_ISSUE_PER_INLINE, true);

        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue1 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        PostJobIssue newIssue2 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        Mockito.when(context.issues()).thenReturn(Arrays.asList(newIssue1, newIssue2));
        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");
        Mockito.when(commitFacade.hasSameCommitCommentsForFile("abc123", inputFile1, 1, ":no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)")).thenReturn(true);
        Mockito.when(commitFacade.hasSameCommitCommentsForFile("abc123", inputFile1, 1, ":no_entry: msg2 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)")).thenReturn(false);

        commitIssuePostJob.execute(context);

        Mockito.verify(commitFacade, never()).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg2 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesInlineNoBody() {
        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, "<#list issues() as issue>\n" +
                "<#if issue.severity != \"MINOR\">\n" +
                "<@p issue=issue/>\n" +
                "</#if>\n" +
                "</#list>\n" +
                "<#macro p issue>\n" +
                "${emojiSeverity(issue.severity)} ${issue.message} [why?](${ruleLink(issue.ruleKey)}) | [View in SonarQube](https://sonar.yourDomain.com/project/issues?id=${issue.componentKey}&issues=${issue.key}&open=${issue.key})\n" +
                "</#macro>");

        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue1 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.MINOR, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        Mockito.when(context.issues()).thenReturn(Arrays.asList(newIssue1));
        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        commitIssuePostJob.execute(context);

        Mockito.verify(commitFacade, never()).createOrUpdateReviewComment("abc123", inputFile1, 1, "");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesGlobalNoBody() {
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE, "");

        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue1 = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.MINOR, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        Mockito.when(context.issues()).thenReturn(Arrays.asList(newIssue1));
        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        commitIssuePostJob.execute(context);

        Mockito.verify(commitFacade, never()).addGlobalComment("");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesAllIssues() {
        settings.setProperty(GitLabPlugin.GITLAB_ALL_ISSUES, true);

        DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
        PostJobIssue newIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        PostJobIssue lineNotVisible = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        DefaultInputFile inputFile2 = new DefaultInputFile("foo", "src/Foo2.php");
        PostJobIssue fileNotInPR = Utils.newMockedIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        PostJobIssue notNewIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        PostJobIssue issueOnDir = Utils.newMockedIssue("foo:src", Severity.BLOCKER, true, "msg4");

        PostJobIssue issueOnProject = Utils.newMockedIssue("foo", Severity.BLOCKER, true, "msg");

        PostJobIssue globalIssue = Utils.newMockedIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(context.issues()).thenReturn(Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        commitIssuePostJob.execute(context);
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("SonarQube analysis reported 7 issues"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("* :no_entry: 7 blocker"));
        Mockito.verify(commitFacade).addGlobalComment(AdditionalMatchers.not(Mockito.contains("1. [Project")));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("1. :no_entry: [msg2](http://gitlab/blob/abc123/src/Foo.php#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)"));

        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("failed", "SonarQube reported 7 issues, with 7 blocker (fail)");
    }
}
