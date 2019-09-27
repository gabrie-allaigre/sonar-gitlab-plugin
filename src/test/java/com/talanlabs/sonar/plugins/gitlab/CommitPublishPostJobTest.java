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
import com.talanlabs.sonar.plugins.gitlab.models.QualityGate;
import com.talanlabs.sonar.plugins.gitlab.models.QualityGateFailMode;
import com.talanlabs.sonar.plugins.gitlab.models.StatusNotificationsMode;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.batch.postjob.internal.DefaultPostJobDescriptor;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.internal.ConfigurationBridge;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.System2;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class CommitPublishPostJobTest {

    private ReporterBuilder reporterBuilder;
    private MapSettings settings;
    private CommitFacade commitFacade;
    private SonarFacade sonarFacade;
    private CommitPublishPostJob commitPublishPostJob;
    private PostJobContext context;

    @Before
    public void prepare() {
        commitFacade = Mockito.mock(CommitFacade.class);
        settings = new MapSettings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL")
                .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue("http://localhost:9000").build()).addComponents(GitLabPlugin.definitions()));
        settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "abc123");
        settings.setProperty("sonar.projectBaseDir", "projectBaseDir");
        settings.setProperty("sonar.working.directory", "workingDir");

        sonarFacade = Mockito.mock(SonarFacade.class);

        reporterBuilder = Mockito.mock(ReporterBuilder.class);

        context = Mockito.mock(PostJobContext.class);
        when(context.config()).thenReturn(new ConfigurationBridge(settings));

        GitLabPluginConfiguration config = new GitLabPluginConfiguration(settings.asConfig(), new System2());

        commitPublishPostJob = new CommitPublishPostJob(config, sonarFacade, commitFacade, reporterBuilder);
    }

    @Test
    public void testDescriptor() {
        DefaultPostJobDescriptor postJobDescriptor = new DefaultPostJobDescriptor();
        commitPublishPostJob.describe(postJobDescriptor);

        Assertions.assertThat(postJobDescriptor.name()).isEqualTo("GitLab Commit Issue Publisher");
        Assertions.assertThat(postJobDescriptor.properties())
                .containsExactly(GitLabPlugin.GITLAB_URL, GitLabPlugin.GITLAB_USER_TOKEN, GitLabPlugin.GITLAB_PROJECT_ID,
                        GitLabPlugin.GITLAB_COMMIT_SHA, GitLabPlugin.GITLAB_REF_NAME, "sonar.projectBaseDir", "sonar.working.directory");
    }

    @Test
    public void testNotEnabled() {
        settings.removeProperty(GitLabPlugin.GITLAB_COMMIT_SHA);

        commitPublishPostJob.execute(context);
        Mockito.verify(reporterBuilder, never()).build(eq(null), any());
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("success", "SonarQube reported no issues");
    }

    @Test
    public void testBaseDirNotFound() {
        settings.removeProperty("sonar.projectBaseDir");

        Assertions.assertThatThrownBy(() -> commitPublishPostJob.execute(context)).isInstanceOf(MessageException.class).hasMessage("SonarQube failed because sonar.projectBaseDir is null");
    }

    @Test
    public void testWorkingDirectoryNotFound() {
        settings.removeProperty("sonar.working.directory");

        Assertions.assertThatThrownBy(() -> commitPublishPostJob.execute(context)).isInstanceOf(MessageException.class).hasMessage("SonarQube failed because sonar.working.directory is null");
    }

    @Test
    public void testUnexpectedException() {

        Assertions.assertThatThrownBy(() -> commitPublishPostJob.execute(context)).isInstanceOf(MessageException.class).hasMessage("SonarQube failed to complete the review of this commit: null");
    }

    @Test
    public void testPreviewModeEmpty() {
        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");

        when(reporterBuilder.build(eq(null), any())).thenReturn(reporter);

        commitPublishPostJob.execute(context);
        Mockito.verify(reporterBuilder).build(eq(null), any());
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("success", "SonarQube reported no issues");
    }

    @Test
    public void testPreviewMode() {
        PostJobIssue issue1 = Utils.newMockedPostJobIssue("foo:src", Severity.BLOCKER, true, "msg4", Utils.newMockedInputComponent("toto"), 0);
        PostJobIssue issue2 = Utils.newMockedPostJobIssue("foo", Severity.BLOCKER, true, "msg", Utils.newMockedInputFile(new File("toto.java")), 0);
        Iterable<PostJobIssue> issues = Arrays.asList(issue1, issue2);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");

        when(reporterBuilder.build(eq(null), any())).thenReturn(reporter);

        commitPublishPostJob.execute(context);
        Mockito.verify(reporterBuilder).build(eq(null), any());
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("success", "SonarQube reported no issues");
    }

    @Test
    public void testIssuesMode() {
        PostJobIssue issue1 = Utils.newMockedPostJobIssue("foo:src", Severity.BLOCKER, true, "msg4");
        PostJobIssue issue2 = Utils.newMockedPostJobIssue("foo", Severity.BLOCKER, true, "msg");
        Iterable<PostJobIssue> issues = Arrays.asList(issue1, issue2);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("failed");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported 2 issues");

        when(reporterBuilder.build(eq(null), any())).thenReturn(reporter);

        commitPublishPostJob.execute(context);
        Mockito.verify(reporterBuilder).build(eq(null), any());
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("failed", "SonarQube reported 2 issues");
    }

    @Test
    public void testPreviewAndIssueMode() {
        PostJobIssue issue1 = Utils.newMockedPostJobIssue("foo:src", Severity.BLOCKER, true, "msg4");
        PostJobIssue issue2 = Utils.newMockedPostJobIssue("foo", Severity.BLOCKER, true, "msg");
        Iterable<PostJobIssue> issues = Arrays.asList(issue1, issue2);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");

        when(reporterBuilder.build(eq(null), any())).thenReturn(reporter);

        commitPublishPostJob.execute(context);
        Mockito.verify(reporterBuilder).build(eq(null), any());
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("success", "SonarQube reported no issues");
    }

    @Test
    public void testSuccess() {
        Iterable<PostJobIssue> issues = Collections.emptyList();

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");

        when(reporterBuilder.build(eq(null), any())).thenReturn(reporter);

        commitPublishPostJob.execute(context);
        Mockito.verify(reporterBuilder).build(eq(null), any());
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("success", "SonarQube reported no issues");
    }

    @Test
    public void testFailed() {
        PostJobIssue issue1 = Utils.newMockedPostJobIssue("foo:src", Severity.BLOCKER, true, "msg4");
        PostJobIssue issue2 = Utils.newMockedPostJobIssue("foo", Severity.BLOCKER, true, "msg");
        Iterable<PostJobIssue> issues = Arrays.asList(issue1, issue2);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("failed");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");

        when(reporterBuilder.build(eq(null), any())).thenReturn(reporter);

        commitPublishPostJob.execute(context);
        Mockito.verify(reporterBuilder).build(eq(null), any());
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("failed", "SonarQube reported no issues");
    }

    @Test
    public void testFailedNotificationExit() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());

        PostJobIssue issue1 = Utils.newMockedPostJobIssue("foo:src", Severity.BLOCKER, true, "msg4");
        PostJobIssue issue2 = Utils.newMockedPostJobIssue("foo", Severity.BLOCKER, true, "msg");
        Iterable<PostJobIssue> issues = Arrays.asList(issue1, issue2);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("failed");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported 2 issues");

        when(reporterBuilder.build(eq(null), any())).thenReturn(reporter);

        Assertions.assertThatThrownBy(() -> commitPublishPostJob.execute(context)).isInstanceOf(MessageException.class).hasMessage("Report status=failed, desc=SonarQube reported 2 issues");
        Mockito.verify(reporterBuilder).build(eq(null), any());
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("failed", "SonarQube reported 2 issues");
    }

    @Test
    public void testSuccessNotificationExit() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());

        Iterable<PostJobIssue> issues = Collections.emptyList();

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");

        when(reporterBuilder.build(eq(null), any())).thenReturn(reporter);

        commitPublishPostJob.execute(context);
        Mockito.verify(reporterBuilder).build(eq(null), any());
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("success", "SonarQube reported no issues");
    }

    @Test
    public void testFailedNotificationNothing() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());

        PostJobIssue issue1 = Utils.newMockedPostJobIssue("foo:src", Severity.BLOCKER, true, "msg4");
        PostJobIssue issue2 = Utils.newMockedPostJobIssue("foo", Severity.BLOCKER, true, "msg");
        Iterable<PostJobIssue> issues = Arrays.asList(issue1, issue2);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("failed");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported 2 issues");

        when(reporterBuilder.build(eq(null), any())).thenReturn(reporter);

        commitPublishPostJob.execute(context);
        Mockito.verify(reporterBuilder).build(eq(null), any());
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("failed", "SonarQube reported 2 issues");
    }

    @Test
    public void testSuccessNotificationNothing() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());

        Iterable<PostJobIssue> issues = Collections.emptyList();

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");

        when(reporterBuilder.build(eq(null), any())).thenReturn(reporter);

        commitPublishPostJob.execute(context);
        Mockito.verify(reporterBuilder).build(eq(null), any());
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("success", "SonarQube reported no issues");
    }


    @Test
    public void testUnexpectedExceptionPublish() {
        // not really realistic unexpected error, but good enough for this test
        when(sonarFacade.loadQualityGate()).thenThrow(new IllegalStateException());

        Assertions.assertThatThrownBy(() -> commitPublishPostJob.execute(context)).isInstanceOf(MessageException.class).hasMessage("SonarQube failed to complete the review of this commit: null");
    }

    @Test
    public void testSuccessPublish() {

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.OK).conditions(Collections.emptyList()).build();
        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube Condition Error:0 Warning:0 Ok:0 SonarQube reported no issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        commitPublishPostJob.execute(context);

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("success", "SonarQube Condition Error:0 Warning:0 Ok:0 SonarQube reported no issues");
    }

    @Test
    public void testFailedPublish() {

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.ERROR).conditions(Arrays.asList(
                QualityGate.Condition.newBuilder().status(QualityGate.Status.ERROR).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build()
        )).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("failed");
        when(reporter.getStatusDescription()).thenReturn("SonarQube Condition Error:1 Warning:2 Ok:3 SonarQube reported 2 issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        commitPublishPostJob.execute(context);

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("failed", "SonarQube Condition Error:1 Warning:2 Ok:3 SonarQube reported 2 issues");
    }

    @Test
    public void testSuccessWithWarm() {

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(Arrays.asList(
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build()
        )).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("failed");
        when(reporter.getStatusDescription()).thenReturn("SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported 2 issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        commitPublishPostJob.execute(context);

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("failed", "SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported 2 issues");
    }

    @Test
    public void testFailedWithWarm() {
        settings.setProperty(GitLabPlugin.GITLAB_QUALITY_GATE_FAIL_MODE, QualityGateFailMode.WARN.getMeaning());

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(Arrays.asList(
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build()
        )).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("failed");
        when(reporter.getStatusDescription()).thenReturn("SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported 2 issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        commitPublishPostJob.execute(context);

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade).createOrUpdateSonarQubeStatus("failed", "SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported 2 issues");
    }

    @Test
    public void testFailedNotificationNothingPublish() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.ERROR).conditions(Arrays.asList(
                QualityGate.Condition.newBuilder().status(QualityGate.Status.ERROR).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build()
        )).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("failed");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported 2 issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        commitPublishPostJob.execute(context);

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("failed", "SonarQube Condition Error:1 Warning:2 Ok:3 SonarQube reported 2 issues");
    }

    @Test
    public void testSuccessNotificationNothingPublish() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.OK).conditions(Collections.emptyList()).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        commitPublishPostJob.execute(context);

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("success", "SonarQube Condition Error:0 Warning:0 Ok:0 SonarQube reported no issues");
    }

    @Test
    public void testSuccessWithWarnNotificationNothing() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(Arrays.asList(
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build()
        )).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        commitPublishPostJob.execute(context);

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("success", "SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported no issues");
    }

    @Test
    public void testFailedWithWarnNotificationNothing() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());
        settings.setProperty(GitLabPlugin.GITLAB_QUALITY_GATE_FAIL_MODE, QualityGateFailMode.WARN.getMeaning());

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(Arrays.asList(
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build()
        )).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        commitPublishPostJob.execute(context);

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("failed", "SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported no issues");
    }

    @Test
    public void testSuccessNotificationExitPublish() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.OK).conditions(Collections.emptyList()).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        commitPublishPostJob.execute(context);

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("success", "SonarQube Condition Error:0 Warning:0 Ok:0 SonarQube reported no issues");
    }

    @Test
    public void testSuccessWithWarnNotificationExit() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(Arrays.asList(
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build()
        )).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        commitPublishPostJob.execute(context);

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("success", "SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported no issues");
    }

    @Test
    public void testFailedNotificationExitPublish() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(Arrays.asList(
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build()
        )).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("success");
        when(reporter.getStatusDescription()).thenReturn("SonarQube reported no issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        commitPublishPostJob.execute(context);

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("failed", "SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported no issues");
    }

    @Test
    public void testFailedWithWarnNotificationExit() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());
        settings.setProperty(GitLabPlugin.GITLAB_QUALITY_GATE_FAIL_MODE, QualityGateFailMode.WARN.getMeaning());

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(Arrays.asList(
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build()
        )).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        Reporter reporter = Mockito.mock(Reporter.class);
        when(reporter.getStatus()).thenReturn("failed");
        when(reporter.getStatusDescription()).thenReturn("SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported no issues");
        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenReturn(reporter);

        Assertions.assertThatThrownBy(() -> commitPublishPostJob.execute(context)).isInstanceOf(MessageException.class)
                .hasMessage("Report status=failed, desc=SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported no issues");

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("failed", "SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported no issues");
    }

    @Test
    public void testFaileReporterNotificationExit() {
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());
        settings.setProperty(GitLabPlugin.GITLAB_QUALITY_GATE_FAIL_MODE, QualityGateFailMode.WARN.getMeaning());

        QualityGate qualityGate = QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(Arrays.asList(
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build(),
                QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("Wrong toto").error("").build()
        )).build();

        when(sonarFacade.loadQualityGate()).thenReturn(qualityGate);

        List<Issue> issues = Collections.emptyList();
        when(reporterBuilder.build(qualityGate, issues)).thenThrow(new IllegalStateException("blabla"));

        Assertions.assertThatThrownBy(() -> commitPublishPostJob.execute(context)).isInstanceOf(MessageException.class).hasMessage("SonarQube failed to complete the review of this commit: blabla");

        Mockito.verify(sonarFacade).loadQualityGate();
        Mockito.verify(reporterBuilder).build(qualityGate, issues);
        Mockito.verify(commitFacade, never()).createOrUpdateSonarQubeStatus("failed", "SonarQube Condition Error:0 Warning:2 Ok:3 SonarQube reported no issues");
    }

}
