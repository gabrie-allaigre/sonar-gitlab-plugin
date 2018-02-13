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

import com.google.protobuf.AbstractMessageLite;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.talanlabs.sonar.plugins.gitlab.models.Issue;
import com.talanlabs.sonar.plugins.gitlab.models.QualityGate;
import okio.Buffer;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.utils.System2;
import org.sonarqube.ws.*;
import org.sonarqube.ws.client.HttpException;

import java.io.*;
import java.text.MessageFormat;
import java.util.List;

public class SonarFacadeTest {

    @Rule
    public MockWebServer sonar = new MockWebServer();
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private Settings settings;
    private SonarFacade sonarFacade;
    private File projectDir;
    private File workDir;

    @Before
    public void prepare() throws IOException {
        settings = new MapSettings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL")
                .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE).build()).addComponents(GitLabPlugin.definitions()));
        settings.setProperty(CoreProperties.SERVER_BASE_URL, String.format("http://%s:%d", sonar.getHostName(), sonar.getPort()));
        settings.setProperty(GitLabPlugin.GITLAB_QUERY_MAX_RETRY, 5);

        projectDir = temp.newFolder();
        workDir = temp.newFolder();

        GitLabPluginConfiguration config = new GitLabPluginConfiguration(settings, new System2());

        sonarFacade = new SonarFacade(settings, config);
        sonarFacade.init(projectDir, workDir);
    }

    private void createReportTaskFile() throws IOException {
        String text = IOUtils.toString(SonarFacadeTest.class.getResourceAsStream("/report-task.txt"), "UTF-8");
        String report = MessageFormat.format(text, "http://" + sonar.getHostName() + ":" + sonar.getPort());
        IOUtils.write(report, new FileOutputStream(new File(workDir, "report-task.txt")), "UTF-8");
    }

    private Buffer toBuffer(AbstractMessageLite messageLite) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        messageLite.writeTo(baos);
        baos.close();
        return new Buffer().write(baos.toByteArray());
    }

    @Test
    public void testNoReportTask() {
        Assertions.assertThatThrownBy(() -> sonarFacade.loadQualityGate()).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(FileNotFoundException.class);
    }

    @Test
    public void testFailed() throws IOException {
        WsCe.TaskResponse taskResponse = WsCe.TaskResponse.newBuilder().setTask(WsCe.Task.newBuilder().setStatus(WsCe.TaskStatus.FAILED).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse)));

        createReportTaskFile();

        Assertions.assertThatThrownBy(() -> sonarFacade.loadQualityGate()).isInstanceOf(IllegalStateException.class).hasMessage("Analyze in SonarQube is not success (FAILED)");
    }

    @Test
    public void testNotFound() throws IOException {
        sonar.enqueue(new MockResponse().setResponseCode(404).setBody("Not Found"));

        createReportTaskFile();

        Assertions.assertThatThrownBy(() -> sonarFacade.loadQualityGate()).isInstanceOf(HttpException.class)
                .hasMessage("Error 404 on http://" + sonar.getHostName() + ":" + sonar.getPort() + "/api/ce/task?id=AVz4Pj0lCGu3nUwPQk4H : Not Found");
    }

    @Test
    public void testSuccess() throws IOException {
        WsCe.TaskResponse taskResponse = WsCe.TaskResponse.newBuilder().setTask(WsCe.Task.newBuilder().setStatus(WsCe.TaskStatus.SUCCESS).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse)));

        WsQualityGates.ProjectStatusWsResponse projectStatusWsResponse = WsQualityGates.ProjectStatusWsResponse.newBuilder()
                .setProjectStatus(WsQualityGates.ProjectStatusWsResponse.ProjectStatus.newBuilder().setStatus(WsQualityGates.ProjectStatusWsResponse.Status.OK).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(projectStatusWsResponse)));

        createReportTaskFile();

        QualityGate qualityGate = sonarFacade.loadQualityGate();
        Assertions.assertThat(qualityGate).isNotNull().extracting(QualityGate::getStatus).contains(QualityGate.Status.OK);
    }

    @Test
    public void testWarning() throws IOException {
        WsCe.TaskResponse taskResponse = WsCe.TaskResponse.newBuilder().setTask(WsCe.Task.newBuilder().setStatus(WsCe.TaskStatus.SUCCESS).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse)));

        WsQualityGates.ProjectStatusWsResponse projectStatusWsResponse = WsQualityGates.ProjectStatusWsResponse.newBuilder()
                .setProjectStatus(WsQualityGates.ProjectStatusWsResponse.ProjectStatus.newBuilder().setStatus(WsQualityGates.ProjectStatusWsResponse.Status.WARN).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(projectStatusWsResponse)));

        createReportTaskFile();

        QualityGate qualityGate = sonarFacade.loadQualityGate();
        Assertions.assertThat(qualityGate).isNotNull().extracting(QualityGate::getStatus).contains(QualityGate.Status.WARN);
    }

    @Test
    public void testSuccessWait() throws IOException {
        for (int i = 0; i < 2; i++) {
            WsCe.TaskResponse taskResponse1 = WsCe.TaskResponse.newBuilder().setTask(WsCe.Task.newBuilder().setStatus(WsCe.TaskStatus.PENDING).build()).build();
            sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse1)));
        }

        WsCe.TaskResponse taskResponse3 = WsCe.TaskResponse.newBuilder().setTask(WsCe.Task.newBuilder().setStatus(WsCe.TaskStatus.SUCCESS).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse3)));

        WsQualityGates.ProjectStatusWsResponse projectStatusWsResponse = WsQualityGates.ProjectStatusWsResponse.newBuilder()
                .setProjectStatus(WsQualityGates.ProjectStatusWsResponse.ProjectStatus.newBuilder().setStatus(WsQualityGates.ProjectStatusWsResponse.Status.OK).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(projectStatusWsResponse)));

        createReportTaskFile();

        QualityGate qualityGate = sonarFacade.loadQualityGate();
        Assertions.assertThat(qualityGate).isNotNull().extracting(QualityGate::getStatus).contains(QualityGate.Status.OK);
    }

    @Test
    public void testSuccessWaitLong() throws IOException {
        for (int i = 0; i < 4; i++) {
            WsCe.TaskResponse taskResponse1 = WsCe.TaskResponse.newBuilder().setTask(WsCe.Task.newBuilder().setStatus(WsCe.TaskStatus.PENDING).build()).build();
            sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse1)));
        }

        WsCe.TaskResponse taskResponse3 = WsCe.TaskResponse.newBuilder().setTask(WsCe.Task.newBuilder().setStatus(WsCe.TaskStatus.SUCCESS).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse3)));

        WsQualityGates.ProjectStatusWsResponse projectStatusWsResponse = WsQualityGates.ProjectStatusWsResponse.newBuilder()
                .setProjectStatus(WsQualityGates.ProjectStatusWsResponse.ProjectStatus.newBuilder().setStatus(WsQualityGates.ProjectStatusWsResponse.Status.OK).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(projectStatusWsResponse)));

        createReportTaskFile();

        QualityGate qualityGate = sonarFacade.loadQualityGate();
        Assertions.assertThat(qualityGate).isNotNull().extracting(QualityGate::getStatus).contains(QualityGate.Status.OK);
    }

    @Test
    public void testFailedWaitLong() throws IOException {
        for (int i = 0; i < 5; i++) {
            WsCe.TaskResponse taskResponse1 = WsCe.TaskResponse.newBuilder().setTask(WsCe.Task.newBuilder().setStatus(WsCe.TaskStatus.PENDING).build()).build();
            sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse1)));
        }

        createReportTaskFile();

        Assertions.assertThatThrownBy(() -> sonarFacade.loadQualityGate()).isInstanceOf(IllegalStateException.class).hasMessage("Report processing is taking longer than the configured wait limit.");
    }

    @Test
    public void testFailedProject() throws IOException {
        WsCe.TaskResponse taskResponse = WsCe.TaskResponse.newBuilder().setTask(WsCe.Task.newBuilder().setStatus(WsCe.TaskStatus.SUCCESS).setAnalysisId("123456").build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse)));

        sonar.enqueue(new MockResponse().setResponseCode(404).setBody("Not Found"));

        createReportTaskFile();

        Assertions.assertThatThrownBy(() -> sonarFacade.loadQualityGate()).isInstanceOf(HttpException.class)
                .hasMessage("Error 404 on http://" + sonar.getHostName() + ":" + sonar.getPort() + "/api/qualitygates/project_status?analysisId=123456 : Not Found");
    }

    @Test
    public void testQualityGateError() throws IOException {
        WsCe.TaskResponse taskResponse = WsCe.TaskResponse.newBuilder().setTask(WsCe.Task.newBuilder().setStatus(WsCe.TaskStatus.SUCCESS).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse)));

        WsQualityGates.ProjectStatusWsResponse projectStatusWsResponse = WsQualityGates.ProjectStatusWsResponse.newBuilder().setProjectStatus(
                WsQualityGates.ProjectStatusWsResponse.ProjectStatus.newBuilder().setStatus(WsQualityGates.ProjectStatusWsResponse.Status.ERROR).addConditions(
                        WsQualityGates.ProjectStatusWsResponse.Condition.newBuilder().setActualValue("10").setMetricKey("security_rating")
                                .setComparator(WsQualityGates.ProjectStatusWsResponse.Comparator.EQ).setStatus(WsQualityGates.ProjectStatusWsResponse.Status.OK).build()).addConditions(
                        WsQualityGates.ProjectStatusWsResponse.Condition.newBuilder().setActualValue("5").setMetricKey("new_sqale_debt_ratio")
                                .setComparator(WsQualityGates.ProjectStatusWsResponse.Comparator.GT).setStatus(WsQualityGates.ProjectStatusWsResponse.Status.WARN).setWarningThreshold("Warning")
                                .build()).addConditions(WsQualityGates.ProjectStatusWsResponse.Condition.newBuilder().setActualValue("100").setMetricKey("new_technical_debt")
                        .setComparator(WsQualityGates.ProjectStatusWsResponse.Comparator.GT).setStatus(WsQualityGates.ProjectStatusWsResponse.Status.ERROR).setErrorThreshold("Error").build())
                        .addConditions(WsQualityGates.ProjectStatusWsResponse.Condition.newBuilder().setActualValue("100").setMetricKey("new_technical_debt")
                                .setComparator(WsQualityGates.ProjectStatusWsResponse.Comparator.GT).setStatus(WsQualityGates.ProjectStatusWsResponse.Status.ERROR).setWarningThreshold("Warning")
                                .setErrorThreshold("Error").build()).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(projectStatusWsResponse)));

        createReportTaskFile();

        QualityGate qualityGate = sonarFacade.loadQualityGate();
        Assertions.assertThat(qualityGate).isNotNull().extracting(QualityGate::getStatus).contains(QualityGate.Status.ERROR);
        Assertions.assertThat(qualityGate.getConditions())
                .extracting(QualityGate.Condition::getActual, QualityGate.Condition::getMetricKey, QualityGate.Condition::getMetricName, QualityGate.Condition::getStatus,
                        QualityGate.Condition::getWarning, QualityGate.Condition::getError).contains(Tuple.tuple("10", "security_rating", "Security Rating", QualityGate.Status.OK, "", ""),
                Tuple.tuple("5", "new_sqale_debt_ratio", "Technical Debt Ratio on New Code", QualityGate.Status.WARN, "Warning", ""),
                Tuple.tuple("100", "new_technical_debt", "Added Technical Debt", QualityGate.Status.ERROR, "", "Error"),
                Tuple.tuple("100", "new_technical_debt", "Added Technical Debt", QualityGate.Status.ERROR, "Warning", "Error"));
    }

    @Test
    public void testEmptyGetNewIssue() throws IOException {
        Issues.SearchWsResponse searchWsResponse = Issues.SearchWsResponse.newBuilder().setTotal(0).setPs(10).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(searchWsResponse)));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isEmpty();
    }

    @Test
    public void testNotEmptyGetNewIssue() throws IOException {
        Issues.SearchWsResponse searchWsResponse = Issues.SearchWsResponse.newBuilder().setTotal(1).setPs(10).addIssues(
                Issues.Issue.newBuilder().setKey("123").setComponent("moi:toto.java").setRule("squid:123").setLine(10).setMessage("Error here").setSeverity(Common.Severity.BLOCKER).setProject("moi")
                        .build()).addComponents(Issues.Component.newBuilder().setKey("moi:toto.java").setQualifier(Qualifiers.FILE).setPath("toto.java").build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(searchWsResponse)));

        WsComponents.ShowWsResponse showWsResponse = WsComponents.ShowWsResponse.newBuilder().build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse)));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isNotEmpty().extracting(Issue::getKey, Issue::getComponentKey, Issue::getSeverity, Issue::getLine, Issue::getMessage, Issue::getRuleKey)
                .contains(Tuple.tuple("123", "moi:toto.java", Severity.BLOCKER, 10, "Error here", "squid:123"));
        Assertions.assertThat(issues.get(0).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "toto.java").getAbsolutePath());
    }

    @Test
    public void tesFullPageGetNewIssue() throws IOException {
        tesFullPageGetNewIssueForQualifier(Qualifiers.FILE);
    }

    @Test
    public void tesFullPageGetNewIssueInTest() throws IOException {
        tesFullPageGetNewIssueForQualifier(Qualifiers.UNIT_TEST_FILE);
    }

    private void tesFullPageGetNewIssueForQualifier(String qualifier) throws IOException {
        Issues.SearchWsResponse.Builder searchWsResponseBuilder = Issues.SearchWsResponse.newBuilder().setTotal(1).setPs(10);
        for (int i = 0; i < 10; i++) {
            searchWsResponseBuilder.addIssues(
                    Issues.Issue.newBuilder().setKey("123").setComponent("moi:toto.java").setRule("squid:123").setLine(10).setMessage("Error here").setSeverity(Common.Severity.BLOCKER)
                            .setProject("moi").build());
        }
        Issues.SearchWsResponse searchWsResponse = searchWsResponseBuilder.addComponents(Issues.Component.newBuilder().setKey("moi:toto.java").setQualifier(qualifier).setPath("toto.java").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(searchWsResponse)));

        WsComponents.ShowWsResponse showWsResponse = WsComponents.ShowWsResponse.newBuilder().build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse)));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isNotEmpty().hasSize(10);
    }

    @Test
    public void tesMultiPageGetNewIssue() throws IOException {
        tesMultiPageGetNewIssueForQualifier(Qualifiers.FILE);
    }

    @Test
    public void tesMultiPageGetNewIssueInTest() throws IOException {
        tesMultiPageGetNewIssueForQualifier(Qualifiers.UNIT_TEST_FILE);
    }

    private void tesMultiPageGetNewIssueForQualifier(String qualifier) throws IOException {
        for (int j = 0; j < 5; j++) {
            Issues.SearchWsResponse.Builder searchWsResponseBuilder = Issues.SearchWsResponse.newBuilder().setTotal(44).setPs(10);
            for (int i = 0; i < (j < 4 ? 10 : 4); i++) {
                searchWsResponseBuilder.addIssues(
                        Issues.Issue.newBuilder().setKey("123").setComponent("moi:toto.java").setRule("squid:123").setLine(10).setMessage("Error here").setSeverity(Common.Severity.BLOCKER)
                                .setProject("moi").build());
            }
            Issues.SearchWsResponse searchWsResponse = searchWsResponseBuilder.addComponents(Issues.Component.newBuilder().setKey("moi:toto.java").setQualifier(qualifier).setPath("toto.java").build())
                    .build();
            sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(searchWsResponse)));

            if (j == 0) {
                WsComponents.ShowWsResponse showWsResponse = WsComponents.ShowWsResponse.newBuilder().build();
                sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse)));

            }
        }

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isNotEmpty().hasSize(44);
    }

    @Test
    public void tesNoFileGetNewIssue() throws IOException {
        Issues.SearchWsResponse.Builder searchWsResponseBuilder = Issues.SearchWsResponse.newBuilder().setTotal(1).setPs(10);
        for (int i = 0; i < 10; i++) {
            searchWsResponseBuilder.addIssues(
                    Issues.Issue.newBuilder().setKey("123").setComponent("moi:toto.java").setRule("squid:123").setLine(10).setMessage("Error here").setSeverity(Common.Severity.BLOCKER)
                            .setProject("moi").build());
        }
        Issues.SearchWsResponse searchWsResponse = searchWsResponseBuilder.addComponents(Issues.Component.newBuilder().setKey("moi:toto.java").setQualifier("BRK").setPath("toto.java").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(searchWsResponse)));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).hasSize(10);
    }

    @Test
    public void tesFailed1GetNewIssue() throws IOException {
        sonar.enqueue(new MockResponse().setResponseCode(404).setBody("Not Found"));

        createReportTaskFile();
        Assertions.assertThatThrownBy(() -> sonarFacade.getNewIssues()).isInstanceOf(HttpException.class)
                .hasMessage("Error 404 on http://" + sonar.getHostName() + ":" + sonar.getPort() + "/api/issues/search?componentKeys=com.talanlabs:avatar-generator-parent&p=1&resolved=false : Not Found");
    }

    @Test
    public void tesFailed2GetNewIssue() {
        Assertions.assertThatThrownBy(() -> sonarFacade.getNewIssues()).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(FileNotFoundException.class);
    }

    private void testNotEmpty2GetNewIssueForQualifier(String qualifier) throws IOException {
        Issues.SearchWsResponse searchWsResponse = Issues.SearchWsResponse.newBuilder().setTotal(1).setPs(10).addIssues(
                Issues.Issue.newBuilder().setKey("123").setComponent("moi:ici:toto.java").setRule("squid:123").setLine(10).setMessage("Error here").setSeverity(Common.Severity.BLOCKER)
                        .setProject("moi").setSubProject("moi:ici").build()).addComponents(Issues.Component.newBuilder().setKey("moi:ici:toto.java").setQualifier(qualifier).setPath("toto.java").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(searchWsResponse)));

        WsComponents.ShowWsResponse showWsResponse = WsComponents.ShowWsResponse.newBuilder().build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse)));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isNotEmpty().extracting(Issue::getKey, Issue::getComponentKey, Issue::getSeverity, Issue::getLine, Issue::getMessage, Issue::getRuleKey)
                .contains(Tuple.tuple("123", "moi:ici:toto.java", Severity.BLOCKER, 10, "Error here", "squid:123"));
        Assertions.assertThat(issues.get(0).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "toto.java").getAbsolutePath());
    }

    @Test
    public void testNotEmpty2GetNewIssue() throws IOException {
        testNotEmpty2GetNewIssueForQualifier(Qualifiers.FILE);
    }

    @Test
    public void testNotEmpty2GetNewIssueInTest() throws IOException {
        testNotEmpty2GetNewIssueForQualifier(Qualifiers.UNIT_TEST_FILE);
    }

    @Test
    public void testNotEmptyGetNewIssueWithComponent() throws IOException {
        Issues.SearchWsResponse searchWsResponse = Issues.SearchWsResponse.newBuilder().setTotal(1).setPs(10).addIssues(
                Issues.Issue.newBuilder().setKey("123").setComponent("moi:toto.java").setRule("squid:123").setLine(10).setMessage("Error here").setSeverity(Common.Severity.BLOCKER).setProject("moi")
                        .build()).addComponents(Issues.Component.newBuilder().setKey("moi:toto.java").setQualifier(Qualifiers.FILE).setPath("toto.java").build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(searchWsResponse)));

        WsComponents.ShowWsResponse showWsResponse = WsComponents.ShowWsResponse.newBuilder()
                .addAncestors(WsComponents.Component.newBuilder().setQualifier("RTG").build())
                .addAncestors(WsComponents.Component.newBuilder().setQualifier(Qualifiers.MODULE).setPath("core").build())
                .addAncestors(WsComponents.Component.newBuilder().setQualifier(Qualifiers.MODULE).setPath("client").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse)));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isNotEmpty().extracting(Issue::getKey, Issue::getComponentKey, Issue::getSeverity, Issue::getLine, Issue::getMessage, Issue::getRuleKey)
                .contains(Tuple.tuple("123", "moi:toto.java", Severity.BLOCKER, 10, "Error here", "squid:123"));
        Assertions.assertThat(issues.get(0).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "client/core/toto.java").getAbsolutePath());
    }

    @Test
    public void testNotEmptyGetNewIssueWithComponentInTest() throws IOException {
        Issues.SearchWsResponse searchWsResponse = Issues.SearchWsResponse.newBuilder().setTotal(1).setPs(10).addIssues(
                Issues.Issue.newBuilder().setKey("123").setComponent("moi:test.java").setRule("squid:123").setLine(42).setMessage("Error here").setSeverity(Common.Severity.MAJOR).setProject("moi")
                        .build()).addComponents(Issues.Component.newBuilder().setKey("moi:test.java").setQualifier(Qualifiers.UNIT_TEST_FILE).setPath("test.java").build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(searchWsResponse)));

        WsComponents.ShowWsResponse showWsResponse = WsComponents.ShowWsResponse.newBuilder()
                .addAncestors(WsComponents.Component.newBuilder().setQualifier("RTG").build())
                .addAncestors(WsComponents.Component.newBuilder().setQualifier(Qualifiers.MODULE).setPath("core").build())
                .addAncestors(WsComponents.Component.newBuilder().setQualifier(Qualifiers.MODULE).setPath("client").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse)));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isNotEmpty().extracting(Issue::getKey, Issue::getComponentKey, Issue::getSeverity, Issue::getLine, Issue::getMessage, Issue::getRuleKey)
                .contains(Tuple.tuple("123", "moi:test.java", Severity.MAJOR, 42, "Error here", "squid:123"));
        Assertions.assertThat(issues.get(0).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "client/core/test.java").getAbsolutePath());
    }

    @Test
    public void testNotEmptyGetNewIssueWithComponents() throws IOException {
        Issues.SearchWsResponse searchWsResponse = Issues.SearchWsResponse.newBuilder().setTotal(1).setPs(10)
                .addIssues(Issues.Issue.newBuilder().setKey("123").setComponent("moi:toto.java").setRule("squid:123").setLine(10).setMessage("Error here").setSeverity(Common.Severity.BLOCKER).setProject("moi").build())
                .addIssues(Issues.Issue.newBuilder().setKey("789").setComponent("moi:toto.java").setRule("squid:123").setLine(10).setMessage("Error here").setSeverity(Common.Severity.BLOCKER).setProject("moi").build())
                .addIssues(Issues.Issue.newBuilder().setKey("456").setComponent("rien:tata.java").setRule("squid:123").setLine(10).setMessage("Error here").setSeverity(Common.Severity.BLOCKER).setProject("moi").build())
                .addIssues(Issues.Issue.newBuilder().setKey("abc").setComponent("moi:test.java").setRule("squid:234").setLine(5).setMessage("Error here").setSeverity(Common.Severity.MAJOR).setProject("moi").build())
                .addComponents(Issues.Component.newBuilder().setKey("moi:toto.java").setQualifier(Qualifiers.FILE).setPath("toto.java").build())
                .addComponents(Issues.Component.newBuilder().setKey("moi:test.java").setQualifier(Qualifiers.UNIT_TEST_FILE).setPath("test.java").build())
                .addComponents(Issues.Component.newBuilder().setKey("rien:tata.java").setQualifier(Qualifiers.FILE).setPath("tata.java").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(searchWsResponse)));

        WsComponents.ShowWsResponse showWsResponse1 = WsComponents.ShowWsResponse.newBuilder()
                .addAncestors(WsComponents.Component.newBuilder().setQualifier("RTG").build())
                .addAncestors(WsComponents.Component.newBuilder().setQualifier(Qualifiers.MODULE).setPath("core").build())
                .addAncestors(WsComponents.Component.newBuilder().setQualifier(Qualifiers.MODULE).setPath("client").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse1)));

        WsComponents.ShowWsResponse showWsResponse2 = WsComponents.ShowWsResponse.newBuilder()
                .addAncestors(WsComponents.Component.newBuilder().setQualifier("RTG").build())
                .addAncestors(WsComponents.Component.newBuilder().setQualifier(Qualifiers.MODULE).setPath("core").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse2)));

        WsComponents.ShowWsResponse showWsResponseTest = WsComponents.ShowWsResponse.newBuilder()
                .addAncestors(WsComponents.Component.newBuilder().setQualifier("RTG").build())
                .addAncestors(WsComponents.Component.newBuilder().setQualifier(Qualifiers.MODULE).setPath("core").build())
                .addAncestors(WsComponents.Component.newBuilder().setQualifier(Qualifiers.MODULE).setPath("client").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponseTest)));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isNotEmpty().extracting(Issue::getKey, Issue::getComponentKey, Issue::getSeverity, Issue::getLine, Issue::getMessage, Issue::getRuleKey)
                .contains(
                        Tuple.tuple("123", "moi:toto.java", Severity.BLOCKER, 10, "Error here", "squid:123"),
                        Tuple.tuple("789", "moi:toto.java", Severity.BLOCKER, 10, "Error here", "squid:123"),
                        Tuple.tuple("456", "rien:tata.java", Severity.BLOCKER, 10, "Error here", "squid:123"),
                        Tuple.tuple("abc", "moi:test.java", Severity.MAJOR, 5, "Error here", "squid:234")
                );
        Assertions.assertThat(issues.get(0).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "client/core/toto.java").getAbsolutePath());
        Assertions.assertThat(issues.get(1).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "client/core/toto.java").getAbsolutePath());
        Assertions.assertThat(issues.get(2).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "core/tata.java").getAbsolutePath());
        Assertions.assertThat(issues.get(3).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "client/core/test.java").getAbsolutePath());
    }

    @Test
    public void testFailedRule() {
        sonar.enqueue(new MockResponse().setResponseCode(404));

        Assertions.assertThatThrownBy(() -> sonarFacade.getRule("toto")).isInstanceOf(IllegalStateException.class).hasMessage("Failed to get rule toto");
    }

    @Test
    public void testSuccessRule() throws IOException {
        Rules.ShowResponse showResponse = Rules.ShowResponse.newBuilder().setRule(Rules.Rule.newBuilder().setType(Common.RuleType.VULNERABILITY).build()).build();

        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showResponse)));

        Assertions.assertThat(sonarFacade.getRule("toto")).isNotNull().extracting(com.talanlabs.sonar.plugins.gitlab.models.Rule::getType).contains(com.talanlabs.sonar.plugins.gitlab.models.Rule.Type.VULNERABILITY);
    }
}
