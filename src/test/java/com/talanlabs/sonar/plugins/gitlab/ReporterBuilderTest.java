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

import com.talanlabs.sonar.plugins.gitlab.models.Issue;
import com.talanlabs.sonar.plugins.gitlab.models.JsonMode;
import com.talanlabs.sonar.plugins.gitlab.models.QualityGate;
import com.talanlabs.sonar.plugins.gitlab.models.QualityGateFailMode;
import com.talanlabs.sonar.plugins.gitlab.models.StatusNotificationsMode;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.System2;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class ReporterBuilderTest {

    private MapSettings settings;
    private ReporterBuilder reporterBuilder;
    private SonarFacade sonarFacade;
    private CommitFacade commitFacade;

    @Before
    public void prepare() {
        sonarFacade = Mockito.mock(SonarFacade.class);
        commitFacade = Mockito.mock(CommitFacade.class);
        settings = new MapSettings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL")
                .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue("http://localhost:9000").build()).addComponents(GitLabPlugin.definitions()));
        settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "abc123");

        GitLabPluginConfiguration config = new GitLabPluginConfiguration(settings.asConfig(), new System2());

        reporterBuilder = new ReporterBuilder(config, sonarFacade, commitFacade, new MarkDownUtils());
    }

    @Test
    public void testCommitAnalysisNoIssue1() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, false);

        Reporter reporter = reporterBuilder.build(null, Collections.emptyList());
        Mockito.verify(commitFacade, Mockito.never()).addGlobalComment(null);
        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("success", "SonarQube reported no issues");
    }

    @Test
    public void testCommitAnalysisNoIssue2() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, true);

        Reporter reporter = reporterBuilder.build(null, Collections.emptyList());
        Mockito.verify(commitFacade).addGlobalComment("SonarQube analysis reported no issues.\n");
        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("success", "SonarQube reported no issues");
    }

    @Test
    public void testCommitAnalysisWithNewIssues() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, false);

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Issue lineNotVisible = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        File inputFile2 = new File("src/Foo2.php");
        Issue fileNotInPR = Utils.newIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        Issue notNewIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        Issue issueOnDir = Utils.newIssue("foo:src", Severity.BLOCKER, true, "msg4");

        Issue issueOnProject = Utils.newIssue("foo", Severity.BLOCKER, true, "msg");

        Issue globalIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        Reporter reporter = reporterBuilder.build(null, Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("SonarQube analysis reported 6 issues"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("* :no_entry: 6 blocker"));
        Mockito.verify(commitFacade).addGlobalComment(AdditionalMatchers.not(Mockito.contains("1. [Project")));
        Mockito.verify(commitFacade)
                .addGlobalComment(Mockito.contains("1. :no_entry: [msg2](http://gitlab/blob/abc123/src/Foo.php#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)"));
        Mockito.verify(sonarFacade, never()).getRule(any());

        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("failed", "SonarQube reported 6 issues, with 6 blocker (fail)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesOnly() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, true);

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Issue lineNotVisible = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        File inputFile2 = new File("src/Foo2.php");
        Issue fileNotInPR = Utils.newIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        Issue notNewIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        Issue issueOnDir = Utils.newIssue("foo:src", Severity.BLOCKER, true, "msg4");

        Issue issueOnProject = Utils.newIssue("foo", Severity.BLOCKER, true, "msg");

        Issue globalIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        Reporter reporter = reporterBuilder.build(null, Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("SonarQube analysis reported 5 issues"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("* :no_entry: 5 blocker"));
        Mockito.verify(commitFacade).addGlobalComment(AdditionalMatchers.not(Mockito.contains("1. [Project")));
        Mockito.verify(commitFacade)
                .addGlobalComment(Mockito.contains("1. :no_entry: [msg2](http://gitlab/blob/abc123/src/Foo.php#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)"));

        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");

        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("failed", "SonarQube reported 5 issues, with 5 blocker (fail)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesOnlyLine() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_LINE, true);

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Issue lineNotVisible = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        File inputFile2 = new File("src/Foo2.php");
        Issue fileNotInPR = Utils.newIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        Issue notNewIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        Issue issueOnDir = Utils.newIssue("foo:src", Severity.BLOCKER, true, "msg4");

        Issue issueOnProject = Utils.newIssue("foo", Severity.BLOCKER, true, "msg");

        Issue globalIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        Reporter reporter = reporterBuilder.build(null, Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("SonarQube analysis reported 1 issue"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("* :no_entry: 1 blocker"));
        Mockito.verify(commitFacade).addGlobalComment(AdditionalMatchers.not(Mockito.contains("1. [Project")));
        Mockito.verify(commitFacade).addGlobalComment(
                AdditionalMatchers.not(Mockito.contains("1. :no_entry: [msg2](http://gitlab/blob/abc123/src/Foo.php#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)")));

        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");

        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("failed", "SonarQube reported 1 issue, with 1 blocker (fail)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesOnlyReview() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, true);

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue1 = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Issue newIssue2 = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Issue newIssue3 = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg3");
        Mockito.when(commitFacade.getGitLabUrl("def456", inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 2)).thenReturn("abc123");

        reporterBuilder.build(null, Arrays.asList(newIssue1, newIssue2, newIssue3));

        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1,
                "* :no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n" + "* :no_entry: msg2 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 2, ":no_entry: msg3 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
    }

    @Test
    public void testSortIssues() {
        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        File inputFile1 = new File("src/Foo.php");
        File inputFile2 = new File("src/Foo2.php");

        // Blocker and 8th line => Should be displayed in 3rd position
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 8, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        // Blocker and 2nd line (Foo2.php) => Should be displayed in 4th position
        Issue issueInSecondFile = Utils.newIssue("foo:src/Foo2.php", inputFile2, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        // Major => Should be displayed in 6th position
        Issue newIssue2 = Utils.newIssue("foo:src/Foo.php", inputFile1, 4, Severity.MAJOR, true, "msg3");

        // Critical => Should be displayed in 5th position
        Issue newIssue3 = Utils.newIssue("foo:src/Foo.php", inputFile1, 3, Severity.CRITICAL, true, "msg4");

        // Critical => Should be displayed in 7th position
        Issue newIssue4 = Utils.newIssue("foo:src/Foo.php", inputFile1, 13, Severity.INFO, true, "msg5");

        // Blocker on project => Should be displayed 1st position
        Issue issueOnProject = Utils.newIssue("foo", Severity.BLOCKER, true, "msg6");

        // Blocker and no line => Should be displayed in 2nd position
        Issue globalIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg7");

        Mockito.when(commitFacade.hasFile(any(File.class))).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(any(File.class), anyInt())).thenReturn(null);

        reporterBuilder.build(null, Arrays.asList(newIssue, globalIssue, issueOnProject, newIssue4, newIssue2, issueInSecondFile, newIssue3));

        Mockito.verify(commitFacade).addGlobalComment(commentCaptor.capture());

        String comment = commentCaptor.getValue();
        Assertions.assertThat(comment).contains("msg6", "msg7", "msg1", "msg2", "msg4", "msg3", "msg5");
    }

    @Test
    public void testCommitAnalysisWithNewCriticalIssues() {
        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.CRITICAL, true, "msg1");
        when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn(null);

        Reporter reporter = reporterBuilder.build(null, Collections.singletonList(newIssue));

        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("failed", "SonarQube reported 1 issue, with 1 critical (fail)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesNoBlockerNorCritical() {
        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.MAJOR, true, "msg1");
        when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn(null);

        Reporter reporter = reporterBuilder.build(null, Collections.singletonList(newIssue));

        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("success", "SonarQube reported 1 issue, with 1 major");
    }

    @Test
    public void testCommitAnalysisWithNewBlockerAndCriticalIssues() {
        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.CRITICAL, true, "msg1");
        when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        Issue lineNotVisible = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn(null);

        Reporter reporter = reporterBuilder.build(null, Arrays.asList(newIssue, lineNotVisible));

        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription)
                .contains("failed", "SonarQube reported 2 issues, with 1 blocker (fail) and 1 critical (fail)");
    }

    @Test
    public void testCommitAnalysisNoIssueExit() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, false);
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());

        reporterBuilder.build(null, Collections.emptyList());
        Mockito.verify(commitFacade, Mockito.never()).addGlobalComment(null);
        Mockito.verify(commitFacade, Mockito.never()).createOrUpdateSonarQubeStatus("success", "SonarQube reported no issues");
    }

    @Test
    public void testCommitAnalysisWithNewIssues2Nothing() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, false);
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_GLOBAL_COMMENT, true);

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        Issue lineNotVisible = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        File inputFile2 = new File("src/Foo2.php");
        Issue fileNotInPR = Utils.newIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        Issue notNewIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        Issue issueOnDir = Utils.newIssue("foo:src", Severity.BLOCKER, true, "msg4");

        Issue issueOnProject = Utils.newIssue("foo", Severity.BLOCKER, true, "msg");

        Issue globalIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn(null);

        reporterBuilder.build(null, Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));

        Mockito.verify(commitFacade, Mockito.never()).addGlobalComment(Mockito.contains("SonarQube analysis reported 6 issues"));
        Mockito.verify(commitFacade, Mockito.never()).createOrUpdateSonarQubeStatus("failed", "SonarQube reported 6 issues, with 6 blocker");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesNoBlockerNorCritical2() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.MAJOR, true, "msg1");
        when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn(null);

        reporterBuilder.build(null, Collections.singletonList(newIssue));

        Mockito.verify(commitFacade, Mockito.never()).createOrUpdateSonarQubeStatus("success", "SonarQube reported 1 issue, no criticals or blockers");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesUniqueInline() {
        settings.setProperty(GitLabPlugin.GITLAB_UNIQUE_ISSUE_PER_INLINE, true);

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue1 = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Issue newIssue2 = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        reporterBuilder.build(null, Arrays.asList(newIssue1, newIssue2));

        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg2 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesExistsComment() {
        settings.setProperty(GitLabPlugin.GITLAB_UNIQUE_ISSUE_PER_INLINE, true);

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue1 = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Issue newIssue2 = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");
        Mockito.when(commitFacade.hasSameCommitCommentsForFile("abc123", inputFile1, 1, ":no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)")).thenReturn(true);
        Mockito.when(commitFacade.hasSameCommitCommentsForFile("abc123", inputFile1, 1, ":no_entry: msg2 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)")).thenReturn(false);

        reporterBuilder.build(null, Arrays.asList(newIssue1, newIssue2));

        Mockito.verify(commitFacade, never()).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg1 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
        Mockito.verify(commitFacade).createOrUpdateReviewComment("abc123", inputFile1, 1, ":no_entry: msg2 [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesInlineNoBody() {
        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE,
                "<#list issues() as issue>\n" + "<#if issue.severity != \"MINOR\">\n" + "<@p issue=issue/>\n" + "</#if>\n" + "</#list>\n" + "<#macro p issue>\n"
                        + "${emojiSeverity(issue.severity)} ${issue.message} [:blue_book:](${ruleLink(issue.ruleKey)})\n" + "</#macro>");

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue1 = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.MINOR, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        reporterBuilder.build(null, Collections.singletonList(newIssue1));

        Mockito.verify(commitFacade, never()).createOrUpdateReviewComment("abc123", inputFile1, 1, "");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesGlobalNoBody() {
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE, "");

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue1 = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.MINOR, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        reporterBuilder.build(null, Collections.singletonList(newIssue1));

        Mockito.verify(commitFacade, never()).addGlobalComment("");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesAllIssues() {
        settings.setProperty(GitLabPlugin.GITLAB_ALL_ISSUES, true);

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Issue lineNotVisible = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        File inputFile2 = new File("src/Foo2.php");
        Issue fileNotInPR = Utils.newIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        Issue notNewIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        Issue issueOnDir = Utils.newIssue("foo:src", Severity.BLOCKER, true, "msg4");

        Issue issueOnProject = Utils.newIssue("foo", Severity.BLOCKER, true, "msg");

        Issue globalIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        Reporter reporter = reporterBuilder.build(null, Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("SonarQube analysis reported 7 issues"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("* :no_entry: 7 blocker"));
        Mockito.verify(commitFacade).addGlobalComment(AdditionalMatchers.not(Mockito.contains("1. [Project")));
        Mockito.verify(commitFacade)
                .addGlobalComment(Mockito.contains("1. :no_entry: [msg2](http://gitlab/blob/abc123/src/Foo.php#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)"));

        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("failed", "SonarQube reported 7 issues, with 7 blocker (fail)");
    }

    @Test
    public void testCommitAnalysisWithNewIssuesSast() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, false);
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());
        settings.setProperty(GitLabPlugin.GITLAB_JSON_MODE, JsonMode.SAST.name());

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Issue lineNotVisible = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        File inputFile2 = new File("src/Foo2.php");
        Issue fileNotInPR = Utils.newIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        Issue notNewIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        Issue issueOnDir = Utils.newIssue("foo:src", Severity.BLOCKER, true, "msg4");

        Issue issueOnProject = Utils.newIssue("foo", Severity.BLOCKER, true, "msg");

        Issue globalIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        reporterBuilder.build(null, Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));

        Mockito.verify(commitFacade).writeJsonFile(Mockito.contains(
                "[{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"msg\",\"file\":\"null\",\"line\":\"0\",\"priority\":\"BLOCKER\",\"solution\":\"http://myserver/coding_rules#rule_key=repo%3Arule\"},{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"msg4\",\"file\":\"null\",\"line\":\"0\",\"priority\":\"BLOCKER\",\"solution\":\"http://myserver/coding_rules#rule_key=repo%3Arule\"},{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"msg5\",\"file\":\"null\",\"line\":\"0\",\"priority\":\"BLOCKER\",\"solution\":\"http://myserver/coding_rules#rule_key=repo%3Arule\"},{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"msg1\",\"file\":\"null\",\"line\":\"1\",\"priority\":\"BLOCKER\",\"solution\":\"http://myserver/coding_rules#rule_key=repo%3Arule\"},{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"msg2\",\"file\":\"null\",\"line\":\"2\",\"priority\":\"BLOCKER\",\"solution\":\"http://myserver/coding_rules#rule_key=repo%3Arule\"},{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"msg3\",\"file\":\"null\",\"line\":\"1\",\"priority\":\"BLOCKER\",\"solution\":\"http://myserver/coding_rules#rule_key=repo%3Arule\"}]"));
    }

    @Test
    public void testCommitAnalysisWithNewIssuesCodeClimate() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, false);
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());
        settings.setProperty(GitLabPlugin.GITLAB_JSON_MODE, JsonMode.CODECLIMATE.name());

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Issue lineNotVisible = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        File inputFile2 = new File("src/Foo2.php");
        Issue fileNotInPR = Utils.newIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        Issue notNewIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        Issue issueOnDir = Utils.newIssue("foo:src", Severity.BLOCKER, true, "msg4");

        Issue issueOnProject = Utils.newIssue("foo", Severity.BLOCKER, true, "msg");

        Issue globalIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        reporterBuilder.build(null, Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));

        Mockito.verify(commitFacade).writeJsonFile(Mockito.contains(
                "[{\"fingerprint\":\"null\",\"description\":\"msg\",\"location\":{\"path\":\"null\",\"lines\": { \"begin\":0,\"end\":0}}},{\"fingerprint\":\"null\",\"description\":\"msg4\",\"location\":{\"path\":\"null\",\"lines\": { \"begin\":0,\"end\":0}}},{\"fingerprint\":\"null\",\"description\":\"msg5\",\"location\":{\"path\":\"null\",\"lines\": { \"begin\":0,\"end\":0}}},{\"fingerprint\":\"null\",\"description\":\"msg1\",\"location\":{\"path\":\"null\",\"lines\": { \"begin\":1,\"end\":1}}},{\"fingerprint\":\"null\",\"description\":\"msg2\",\"location\":{\"path\":\"null\",\"lines\": { \"begin\":2,\"end\":2}}},{\"fingerprint\":\"null\",\"description\":\"msg3\",\"location\":{\"path\":\"null\",\"lines\": { \"begin\":1,\"end\":1}}}]"));
    }

    @Test
    public void testCommitAnalysisWithNewIssuesNone() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, false);
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());
        settings.setProperty(GitLabPlugin.GITLAB_JSON_MODE, JsonMode.NONE.name());

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Issue lineNotVisible = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        File inputFile2 = new File("src/Foo2.php");
        Issue fileNotInPR = Utils.newIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        Issue notNewIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        Issue issueOnDir = Utils.newIssue("foo:src", Severity.BLOCKER, true, "msg4");

        Issue issueOnProject = Utils.newIssue("foo", Severity.BLOCKER, true, "msg");

        Issue globalIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        reporterBuilder.build(null, Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));

        Mockito.verify(commitFacade, never()).writeJsonFile(any());
    }

    @Test
    public void testFilterIssue() {
        settings.setProperty(GitLabPlugin.GITLAB_ALL_ISSUES, true);
        settings.setProperty(GitLabPlugin.GITLAB_ISSUE_FILTER, Severity.CRITICAL.name());

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Issue lineNotVisible = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.MAJOR, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        File inputFile2 = new File("src/Foo2.php");
        Issue fileNotInPR = Utils.newIssue("foo:src/Foo2.php", inputFile2, 1, Severity.MINOR, true, "msg3");

        Issue notNewIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.INFO, false, "msg");

        Issue issueOnDir = Utils.newIssue("foo:src", Severity.CRITICAL, true, "msg4");

        Issue issueOnProject = Utils.newIssue("foo", Severity.CRITICAL, true, "msg");

        Issue globalIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        Reporter reporter = reporterBuilder.build(null, Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("SonarQube analysis reported 4 issues"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("* :no_entry: 2 blocker"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("* :no_entry_sign: 2 critical"));
        Mockito.verify(commitFacade).addGlobalComment(AdditionalMatchers.not(Mockito.contains("1. [Project")));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("1. :no_entry: msg5 (foo:src/Foo.php) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("1. :no_entry_sign: msg (foo) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)"));
        Mockito.verify(commitFacade).addGlobalComment(Mockito.contains("1. :no_entry_sign: msg4 (foo:src) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)"));

        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription)
                .contains("failed", "SonarQube reported 4 issues, with 2 blocker (fail) and 2 critical (fail)");
    }

    @Test
    public void testCommitAnalysisQualityGateNoIssue1() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, false);

        Reporter reporter = reporterBuilder.build(QualityGate.newBuilder().status(QualityGate.Status.OK).conditions(Collections.emptyList()).build(), Collections.emptyList());
        Mockito.verify(commitFacade, Mockito.never()).addGlobalComment(null);
        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("success", "SonarQube reported QualityGate is ok, with no conditions, no issues");
    }

    @Test
    public void testCommitAnalysisQualityGateNoIssue2() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, true);

        Reporter reporter = reporterBuilder.build(QualityGate.newBuilder().status(QualityGate.Status.OK).conditions(Collections.emptyList()).build(), Collections.emptyList());
        Mockito.verify(commitFacade).addGlobalComment("SonarQube analysis indicates that quality gate is passed.\n\nSonarQube analysis reported no issues.\n");
        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("success", "SonarQube reported QualityGate is ok, with no conditions, no issues");
    }

    @Test
    public void testCommitAnalysisQualityGateNoIssue3() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, false);

        List<QualityGate.Condition> conditions = new ArrayList<>();
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto1").actual("10").symbol("<").warning("").error("0").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto2").actual("11").symbol(">=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto3").actual("13").symbol("<=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto4").actual("14").symbol(">").warning("20").error("30").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto5").actual("15").symbol("=").warning("10").error("").build());

        Reporter reporter = reporterBuilder.build(QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(conditions).build(), Collections.emptyList());
        Mockito.verify(commitFacade).addGlobalComment(
                "SonarQube analysis indicates that quality gate is warning.\n" + "* Toto1 is passed: Actual value 10\n" + "* Toto2 is passed: Actual value 11\n"
                        + "* Toto3 is passed: Actual value 13\n" + "* Toto4 is warning: Actual value 14 > 20\n" + "* Toto5 is warning: Actual value 15 = 10\n" + "\n"
                        + "SonarQube analysis reported no issues.\n");
        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("success", "SonarQube reported QualityGate is warn, with 2 warn and 3 ok, no issues");
    }

    @Test
    public void testCommitAnalysisQualityGateNoIssue4() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, false);

        List<QualityGate.Condition> conditions = new ArrayList<>();
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.ERROR).metricKey("toto").metricName("Toto1").actual("10").symbol("<").warning("").error("0").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.ERROR).metricKey("toto").metricName("Toto2").actual("11").symbol(">=").warning("").error("10").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto3").actual("13").symbol("<=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto4").actual("14").symbol(">").warning("20").error("30").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto5").actual("15").symbol("=").warning("10").error("").build());

        Reporter reporter = reporterBuilder.build(QualityGate.newBuilder().status(QualityGate.Status.ERROR).conditions(conditions).build(), Collections.emptyList());
        Mockito.verify(commitFacade).addGlobalComment(
                "SonarQube analysis indicates that quality gate is failed.\n" + "* Toto1 is failed: Actual value 10 < 0\n" + "* Toto2 is failed: Actual value 11 >= 10\n"
                        + "* Toto3 is passed: Actual value 13\n" + "* Toto4 is warning: Actual value 14 > 20\n" + "* Toto5 is warning: Actual value 15 = 10\n" + "\n"
                        + "SonarQube analysis reported no issues.\n");
        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("failed", "SonarQube reported QualityGate is error, with 2 error and 2 warn and 1 ok, no issues");
    }

    @Test
    public void testCommitAnalysisQualityGateFailModeWarning() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, false);
        settings.setProperty(GitLabPlugin.GITLAB_QUALITY_GATE_FAIL_MODE, QualityGateFailMode.WARN.getMeaning());

        List<QualityGate.Condition> conditions = new ArrayList<>();
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto1").actual("10").symbol("<").warning("").error("0").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto2").actual("11").symbol(">=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto3").actual("13").symbol("<=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto4").actual("14").symbol(">").warning("20").error("30").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto5").actual("15").symbol("=").warning("10").error("").build());

        Reporter reporter = reporterBuilder.build(QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(conditions).build(), Collections.emptyList());
        Mockito.verify(commitFacade).addGlobalComment(
            "SonarQube analysis indicates that quality gate is warning.\n" + "* Toto1 is passed: Actual value 10\n" + "* Toto2 is passed: Actual value 11\n"
                + "* Toto3 is passed: Actual value 13\n" + "* Toto4 is warning: Actual value 14 > 20\n" + "* Toto5 is warning: Actual value 15 = 10\n" + "\n"
                + "SonarQube analysis reported no issues.\n");
        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("failed", "SonarQube reported QualityGate is warn, with 2 warn and 3 ok, no issues");
    }

    @Test
    public void testCommitAnalysisQualityGateFailModeNone() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, false);
        settings.setProperty(GitLabPlugin.GITLAB_QUALITY_GATE_FAIL_MODE, QualityGateFailMode.NONE.getMeaning());

        List<QualityGate.Condition> conditions = new ArrayList<>();
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.ERROR).metricKey("toto").metricName("Toto1").actual("10").symbol("<").warning("").error("0").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.ERROR).metricKey("toto").metricName("Toto2").actual("11").symbol(">=").warning("").error("10").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto3").actual("13").symbol("<=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto4").actual("14").symbol(">").warning("20").error("30").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto5").actual("15").symbol("=").warning("10").error("").build());

        Reporter reporter = reporterBuilder.build(QualityGate.newBuilder().status(QualityGate.Status.ERROR).conditions(conditions).build(), Collections.emptyList());
        Mockito.verify(commitFacade).addGlobalComment(
            "SonarQube analysis indicates that quality gate is failed.\n" + "* Toto1 is failed: Actual value 10 < 0\n" + "* Toto2 is failed: Actual value 11 >= 10\n"
                + "* Toto3 is passed: Actual value 13\n" + "* Toto4 is warning: Actual value 14 > 20\n" + "* Toto5 is warning: Actual value 15 = 10\n" + "\n"
                + "SonarQube analysis reported no issues.\n");
        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("success", "SonarQube reported QualityGate is error, with 2 error and 2 warn and 1 ok, no issues");
    }

    @Test
    public void testCommitAnalysisQualityGateIssues() {
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, false);

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");
        Mockito.when(commitFacade.getRuleLink("repo:rule")).thenReturn("http://myserver/coding_rules#rule_key=repo%3Arule");

        Issue lineNotVisible = Utils.newIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
        Mockito.when(commitFacade.getGitLabUrl(null, inputFile1, 2)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L2");

        File inputFile2 = new File("src/Foo2.php");
        Issue fileNotInPR = Utils.newIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

        Issue notNewIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

        Issue issueOnDir = Utils.newIssue("foo:src", Severity.BLOCKER, true, "msg4");

        Issue issueOnProject = Utils.newIssue("foo", Severity.BLOCKER, true, "msg");

        Issue globalIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

        Mockito.when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        Mockito.when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn("abc123");

        List<QualityGate.Condition> conditions = new ArrayList<>();
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.ERROR).metricKey("toto").metricName("Toto1").actual("10").symbol("<").warning("").error("0").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.ERROR).metricKey("toto").metricName("Toto2").actual("11").symbol(">=").warning("").error("10").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto3").actual("13").symbol("<=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto4").actual("14").symbol(">").warning("20").error("30").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto5").actual("15").symbol("=").warning("10").error("").build());

        Reporter reporter = reporterBuilder.build(QualityGate.newBuilder().status(QualityGate.Status.OK).conditions(conditions).build(), Arrays.asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));

        Mockito.verify(commitFacade).addGlobalComment(
                "SonarQube analysis indicates that quality gate is passed.\n" + "* Toto1 is failed: Actual value 10 < 0\n" + "* Toto2 is failed: Actual value 11 >= 10\n"
                        + "* Toto3 is passed: Actual value 13\n" + "* Toto4 is warning: Actual value 14 > 20\n" + "* Toto5 is warning: Actual value 15 = 10\n" + "\n"
                        + "SonarQube analysis reported 6 issues\n" + "* :no_entry: 6 blocker\n" + "\n" + "Watch the comments in this conversation to review them.\n" + "\n" + "#### 5 extra issues\n"
                        + "\n"
                        + "Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:\n"
                        + "\n" + "1. :no_entry: msg (foo) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "1. :no_entry: msg4 (foo:src) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "1. :no_entry: msg5 (foo:src/Foo.php) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "1. :no_entry: [msg2](http://gitlab/blob/abc123/src/Foo.php#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "1. :no_entry: msg3 (foo:src/Foo2.php) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n");
        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("failed", "SonarQube reported QualityGate is ok, with 2 error and 2 warn and 1 ok, 6 issues, with 6 blocker (fail)");
    }

    @Test
    public void testCommitAnalysisWithNewCriticalIssuesRule() {
        settings.setProperty(GitLabPlugin.GITLAB_LOAD_RULES, "true");

        File inputFile1 = new File("src/Foo.php");
        Issue newIssue = Utils.newIssue("foo:src/Foo.php", inputFile1, 1, Severity.CRITICAL, true, "msg1");
        when(commitFacade.getGitLabUrl(null, inputFile1, 1)).thenReturn("http://gitlab/blob/abc123/src/Foo.php#L1");

        when(commitFacade.hasFile(inputFile1)).thenReturn(true);
        when(commitFacade.getRevisionForLine(inputFile1, 1)).thenReturn(null);

        Reporter reporter = reporterBuilder.build(null, Collections.singletonList(newIssue));

        Assertions.assertThat(reporter).isNotNull().extracting(Reporter::getStatus, Reporter::getStatusDescription).contains("failed", "SonarQube reported 1 issue, with 1 critical (fail)");

        Mockito.verify(sonarFacade).getRule("repo:rule");
    }
}
