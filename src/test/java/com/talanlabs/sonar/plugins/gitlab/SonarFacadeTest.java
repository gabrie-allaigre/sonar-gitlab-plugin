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
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.utils.System2;
import org.sonarqube.ws.Ce;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Components;
import org.sonarqube.ws.Issues;
import org.sonarqube.ws.Qualitygates;
import org.sonarqube.ws.Rules;
import org.sonarqube.ws.client.HttpException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

public class SonarFacadeTest {

    @Rule
    public MockWebServer sonar = new MockWebServer();
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private MapSettings settings;
    private SonarFacade sonarFacade;
    private File projectDir;
    private File workDir;

    @Before
    public void prepare() throws IOException {
        settings = new MapSettings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL")
                .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue("http://localhost:9000").build()).addComponents(GitLabPlugin.definitions()));
        settings.setProperty(CoreProperties.SERVER_BASE_URL, String.format("http://%s:%d", sonar.getHostName(), sonar.getPort()));
        settings.setProperty(GitLabPlugin.GITLAB_QUERY_MAX_RETRY, 5);

        projectDir = temp.newFolder();
        workDir = temp.newFolder();

        GitLabPluginConfiguration config = new GitLabPluginConfiguration(settings.asConfig(), new System2());

        sonarFacade = new SonarFacade(settings.asConfig(), config);
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
        Ce.TaskResponse taskResponse = Ce.TaskResponse.newBuilder().setTask(Ce.Task.newBuilder().setStatus(Ce.TaskStatus.FAILED).build()).build();
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
        Ce.TaskResponse taskResponse = Ce.TaskResponse.newBuilder().setTask(Ce.Task.newBuilder().setStatus(Ce.TaskStatus.SUCCESS).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse)));

        Qualitygates.ProjectStatusResponse projectStatusWsResponse = Qualitygates.ProjectStatusResponse.newBuilder()
                .setProjectStatus(Qualitygates.ProjectStatusResponse.ProjectStatus.newBuilder().setStatus(Qualitygates.ProjectStatusResponse.Status.OK).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(projectStatusWsResponse)));

        createReportTaskFile();

        QualityGate qualityGate = sonarFacade.loadQualityGate();
        Assertions.assertThat(qualityGate).isNotNull().extracting(QualityGate::getStatus).contains(QualityGate.Status.OK);
    }

    @Test
    public void testWarning() throws IOException {
        Ce.TaskResponse taskResponse = Ce.TaskResponse.newBuilder().setTask(Ce.Task.newBuilder().setStatus(Ce.TaskStatus.SUCCESS).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse)));

        Qualitygates.ProjectStatusResponse projectStatusWsResponse = Qualitygates.ProjectStatusResponse.newBuilder()
                .setProjectStatus(Qualitygates.ProjectStatusResponse.ProjectStatus.newBuilder().setStatus(Qualitygates.ProjectStatusResponse.Status.WARN).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(projectStatusWsResponse)));

        createReportTaskFile();

        QualityGate qualityGate = sonarFacade.loadQualityGate();
        Assertions.assertThat(qualityGate).isNotNull().extracting(QualityGate::getStatus).contains(QualityGate.Status.WARN);
    }

    @Test
    public void testSuccessWait() throws IOException {
        for (int i = 0; i < 2; i++) {
            Ce.TaskResponse taskResponse1 = Ce.TaskResponse.newBuilder().setTask(Ce.Task.newBuilder().setStatus(Ce.TaskStatus.PENDING).build()).build();
            sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse1)));
        }

        Ce.TaskResponse taskResponse3 = Ce.TaskResponse.newBuilder().setTask(Ce.Task.newBuilder().setStatus(Ce.TaskStatus.SUCCESS).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse3)));

        Qualitygates.ProjectStatusResponse projectStatusWsResponse = Qualitygates.ProjectStatusResponse.newBuilder()
                .setProjectStatus(Qualitygates.ProjectStatusResponse.ProjectStatus.newBuilder().setStatus(Qualitygates.ProjectStatusResponse.Status.OK).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(projectStatusWsResponse)));

        createReportTaskFile();

        QualityGate qualityGate = sonarFacade.loadQualityGate();
        Assertions.assertThat(qualityGate).isNotNull().extracting(QualityGate::getStatus).contains(QualityGate.Status.OK);
    }

    @Test
    public void testSuccessWaitLong() throws IOException {
        for (int i = 0; i < 4; i++) {
            Ce.TaskResponse taskResponse1 = Ce.TaskResponse.newBuilder().setTask(Ce.Task.newBuilder().setStatus(Ce.TaskStatus.PENDING).build()).build();
            sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse1)));
        }

        Ce.TaskResponse taskResponse3 = Ce.TaskResponse.newBuilder().setTask(Ce.Task.newBuilder().setStatus(Ce.TaskStatus.SUCCESS).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse3)));

        Qualitygates.ProjectStatusResponse projectStatusWsResponse = Qualitygates.ProjectStatusResponse.newBuilder()
                .setProjectStatus(Qualitygates.ProjectStatusResponse.ProjectStatus.newBuilder().setStatus(Qualitygates.ProjectStatusResponse.Status.OK).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(projectStatusWsResponse)));

        createReportTaskFile();

        QualityGate qualityGate = sonarFacade.loadQualityGate();
        Assertions.assertThat(qualityGate).isNotNull().extracting(QualityGate::getStatus).contains(QualityGate.Status.OK);
    }

    @Test
    public void testFailedWaitLong() throws IOException {
        for (int i = 0; i < 5; i++) {
            Ce.TaskResponse taskResponse1 = Ce.TaskResponse.newBuilder().setTask(Ce.Task.newBuilder().setStatus(Ce.TaskStatus.PENDING).build()).build();
            sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse1)));
        }

        createReportTaskFile();

        Assertions.assertThatThrownBy(() -> sonarFacade.loadQualityGate()).isInstanceOf(IllegalStateException.class).hasMessage("Report processing is taking longer than the configured wait limit.");
    }

    @Test
    public void testFailedProject() throws IOException {
        Ce.TaskResponse taskResponse = Ce.TaskResponse.newBuilder().setTask(Ce.Task.newBuilder().setStatus(Ce.TaskStatus.SUCCESS).setAnalysisId("123456").build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse)));

        sonar.enqueue(new MockResponse().setResponseCode(404).setBody("Not Found"));

        createReportTaskFile();

        Assertions.assertThatThrownBy(() -> sonarFacade.loadQualityGate()).isInstanceOf(HttpException.class)
                .hasMessage("Error 404 on http://" + sonar.getHostName() + ":" + sonar.getPort() + "/api/qualitygates/project_status?analysisId=123456 : Not Found");
    }

    @Test
    public void testQualityGateError() throws IOException {
        Ce.TaskResponse taskResponse = Ce.TaskResponse.newBuilder().setTask(Ce.Task.newBuilder().setStatus(Ce.TaskStatus.SUCCESS).build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(taskResponse)));

        Qualitygates.ProjectStatusResponse projectStatusWsResponse = Qualitygates.ProjectStatusResponse.newBuilder().setProjectStatus(
                Qualitygates.ProjectStatusResponse.ProjectStatus.newBuilder().setStatus(Qualitygates.ProjectStatusResponse.Status.ERROR).addConditions(
                        Qualitygates.ProjectStatusResponse.Condition.newBuilder().setActualValue("10").setMetricKey("security_rating")
                                .setComparator(Qualitygates.ProjectStatusResponse.Comparator.EQ).setStatus(Qualitygates.ProjectStatusResponse.Status.OK).build()).addConditions(
                        Qualitygates.ProjectStatusResponse.Condition.newBuilder().setActualValue("5").setMetricKey("new_sqale_debt_ratio")
                                .setComparator(Qualitygates.ProjectStatusResponse.Comparator.GT).setStatus(Qualitygates.ProjectStatusResponse.Status.WARN).setWarningThreshold("Warning")
                                .build()).addConditions(Qualitygates.ProjectStatusResponse.Condition.newBuilder().setActualValue("100").setMetricKey("new_technical_debt")
                        .setComparator(Qualitygates.ProjectStatusResponse.Comparator.GT).setStatus(Qualitygates.ProjectStatusResponse.Status.ERROR).setErrorThreshold("Error").build())
                        .addConditions(Qualitygates.ProjectStatusResponse.Condition.newBuilder().setActualValue("100").setMetricKey("new_technical_debt")
                                .setComparator(Qualitygates.ProjectStatusResponse.Comparator.GT).setStatus(Qualitygates.ProjectStatusResponse.Status.ERROR).setWarningThreshold("Warning")
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

        Components.ShowWsResponse showWsResponse = Components.ShowWsResponse.newBuilder().build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse)));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isNotEmpty().extracting(Issue::getKey, Issue::getComponentKey, Issue::getSeverity, Issue::getLine, Issue::getMessage, Issue::getRuleKey)
                .contains(Tuple.tuple("123", "moi:toto.java", Severity.BLOCKER, 10, "Error here", "squid:123"));
        Assertions.assertThat(issues.get(0).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "toto.java").getAbsolutePath());
    }

    @Test
    public void test10000NewIssue() throws IOException {
        Issues.SearchWsResponse.Builder builder = Issues.SearchWsResponse.newBuilder().setTotal(20000).setPs(100);
        for (int i = 0; i < 100; i++) {
            builder.addIssues(Issues.Issue.newBuilder().build());
        }
        Issues.SearchWsResponse searchWsResponse = builder.build();
        for (int i = 0; i < 100; i++) {
            sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(searchWsResponse)));
        }
        sonar.enqueue(new MockResponse().setResponseCode(400).setBody("{\"errors\":[{\"msg\":\"Can return only the first 10000 results. 10100th result asked.\"}]}"));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().hasSize(10000);
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

        Components.ShowWsResponse showWsResponse = Components.ShowWsResponse.newBuilder().build();
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
                Components.ShowWsResponse showWsResponse = Components.ShowWsResponse.newBuilder().build();
                sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse)));

            }
        }

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isNotEmpty().hasSize(10);
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
                .hasMessage("Error 404 on http://" + sonar.getHostName() + ":" + sonar.getPort() + "/api/issues/search?componentKeys=com.talanlabs%3Aavatar-generator-parent&p=1&resolved=false : Not Found");
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

        Components.ShowWsResponse showWsResponse = Components.ShowWsResponse.newBuilder().build();
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

        Components.ShowWsResponse showWsResponse = Components.ShowWsResponse.newBuilder()
                .addAncestors(Components.Component.newBuilder().setQualifier("RTG").build())
                .addAncestors(Components.Component.newBuilder().setQualifier(Qualifiers.PROJECT).setPath("core/client").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse)));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isNotEmpty().extracting(Issue::getKey, Issue::getComponentKey, Issue::getSeverity, Issue::getLine, Issue::getMessage, Issue::getRuleKey)
                .contains(Tuple.tuple("123", "moi:toto.java", Severity.BLOCKER, 10, "Error here", "squid:123"));
        Assertions.assertThat(issues.get(0).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "toto.java").getAbsolutePath());
    }

    @Test
    public void testNotEmptyGetNewIssueWithComponentInTest() throws IOException {
        Issues.SearchWsResponse searchWsResponse = Issues.SearchWsResponse.newBuilder().setTotal(1).setPs(10).addIssues(
                Issues.Issue.newBuilder().setKey("123").setComponent("moi:test.java").setRule("squid:123").setLine(42).setMessage("Error here").setSeverity(Common.Severity.MAJOR).setProject("moi")
                        .build()).addComponents(Issues.Component.newBuilder().setKey("moi:test.java").setQualifier(Qualifiers.UNIT_TEST_FILE).setPath("test.java").build()).build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(searchWsResponse)));

        Components.ShowWsResponse showWsResponse = Components.ShowWsResponse.newBuilder()
                .addAncestors(Components.Component.newBuilder().setQualifier("RTG").build())
                .addAncestors(Components.Component.newBuilder().setQualifier(Qualifiers.PROJECT).setPath("client/core").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse)));

        createReportTaskFile();

        List<Issue> issues = sonarFacade.getNewIssues();
        Assertions.assertThat(issues).isNotNull().isNotEmpty().extracting(Issue::getKey, Issue::getComponentKey, Issue::getSeverity, Issue::getLine, Issue::getMessage, Issue::getRuleKey)
                .contains(Tuple.tuple("123", "moi:test.java", Severity.MAJOR, 42, "Error here", "squid:123"));
        Assertions.assertThat(issues.get(0).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "test.java").getAbsolutePath());
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

        Components.ShowWsResponse showWsResponse1 = Components.ShowWsResponse.newBuilder()
                .addAncestors(Components.Component.newBuilder().setQualifier("RTG").build())
                .addAncestors(Components.Component.newBuilder().setQualifier(Qualifiers.PROJECT).setPath("core").build())
                .addAncestors(Components.Component.newBuilder().setQualifier(Qualifiers.PROJECT).setPath("client").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse1)));

        Components.ShowWsResponse showWsResponse2 = Components.ShowWsResponse.newBuilder()
                .addAncestors(Components.Component.newBuilder().setQualifier("RTG").build())
                .addAncestors(Components.Component.newBuilder().setQualifier(Qualifiers.PROJECT).setPath("core").build())
                .build();
        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showWsResponse2)));

        Components.ShowWsResponse showWsResponseTest = Components.ShowWsResponse.newBuilder()
                .addAncestors(Components.Component.newBuilder().setQualifier("RTG").build())
                .addAncestors(Components.Component.newBuilder().setQualifier(Qualifiers.PROJECT).setPath("core").build())
                .addAncestors(Components.Component.newBuilder().setQualifier(Qualifiers.PROJECT).setPath("client").build())
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
        Assertions.assertThat(issues.get(0).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "toto.java").getAbsolutePath());
        Assertions.assertThat(issues.get(1).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "toto.java").getAbsolutePath());
        Assertions.assertThat(issues.get(2).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "tata.java").getAbsolutePath());
        Assertions.assertThat(issues.get(3).getFile().getAbsolutePath()).isEqualTo(new File(projectDir, "test.java").getAbsolutePath());
    }

    @Test
    public void testFailedRule() {
        sonar.enqueue(new MockResponse().setResponseCode(404));

        Assertions.assertThatThrownBy(() -> sonarFacade.getRule("toto")).isInstanceOf(IllegalStateException.class).hasMessage("Failed to get rule toto");
    }

    @Test
    public void testSuccessRule() throws IOException {
        Rules.ShowResponse showResponse = Rules.ShowResponse.newBuilder().setRule(Rules.Rule.newBuilder().setKey("toto").setRepo("repo").setName("Toto").setMdDesc("Hello").setType(Common.RuleType.VULNERABILITY).setDebtRemFnType("rien").setRemFnBaseEffort("ici").build()).build();

        sonar.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/x-protobuf").setBody(toBuffer(showResponse)));

        Assertions.assertThat(sonarFacade.getRule("toto")).isNotNull()
                .extracting(
                        com.talanlabs.sonar.plugins.gitlab.models.Rule::getKey,
                        com.talanlabs.sonar.plugins.gitlab.models.Rule::getRepo,
                        com.talanlabs.sonar.plugins.gitlab.models.Rule::getName,
                        com.talanlabs.sonar.plugins.gitlab.models.Rule::getDescription,
                        com.talanlabs.sonar.plugins.gitlab.models.Rule::getType,
                        com.talanlabs.sonar.plugins.gitlab.models.Rule::getDebtRemFnBaseEffort,
                        com.talanlabs.sonar.plugins.gitlab.models.Rule::getDebtRemFnType
                ).containsExactly(
                "toto", "repo", "Toto", "Hello",
                "VULNERABILITY",
                "ici", "rien"
        );
    }

    @Test
    public void testMetricNameFailed() {
        Assertions.assertThat(sonarFacade.getMetricName("toto")).isEqualTo("toto");
        Assertions.assertThat(sonarFacade.getMetricName("security_rating")).isEqualTo("Security Rating");
    }

}
