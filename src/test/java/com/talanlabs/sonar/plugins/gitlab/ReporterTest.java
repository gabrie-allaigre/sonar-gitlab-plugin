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

import com.talanlabs.sonar.plugins.gitlab.models.JsonMode;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.System2;

public class ReporterTest {

    private static final String GITLAB_URL = "https://gitlab.com/test/test";

    private Settings settings;
    private GitLabPluginConfiguration config;
    private Reporter reporter;

    @Before
    public void setup() {
        settings = new Settings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL").description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE).build()).addComponents(GitLabPlugin.definitions()));

        settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "abc123");

        config = new GitLabPluginConfiguration(settings, new System2());
        reporter = new Reporter(config);
    }

    @Test
    public void noIssues() {
        Assertions.assertThat(reporter.getIssueCount()).isEqualTo(0);
    }

    @Test
    public void oneIssue() {
        reporter.process(Utils.newIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver", true);

        Assertions.assertThat(reporter.getIssueCount()).isEqualTo(1);
        Assertions.assertThat(reporter.getNotReportedIssueCount()).isEqualTo(0);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.INFO)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.BLOCKER)).isEqualTo(0);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.CRITICAL)).isEqualTo(0);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MAJOR)).isEqualTo(0);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MINOR)).isEqualTo(0);
        Assertions.assertThat(reporter.getReportIssues()).hasSize(1);
    }

    @Test
    public void shouldFormatIssuesForMarkdownNoInline() {
        reporter.process(Utils.newIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.MINOR, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.CRITICAL, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.BLOCKER, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver", true);

        Assertions.assertThat(reporter.getIssueCount()).isEqualTo(5);
        Assertions.assertThat(reporter.getNotReportedIssueCount()).isEqualTo(0);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.INFO)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.BLOCKER)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.CRITICAL)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MAJOR)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MINOR)).isEqualTo(1);
        Assertions.assertThat(reporter.getReportIssues()).hasSize(5);
    }

    @Test
    public void shouldFormatIssuesForMarkdownMixInlineGlobal() {
        reporter.process(Utils.newIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), null, null, GITLAB_URL, "file", "http://myserver", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), null, null, GITLAB_URL, "file", "http://myserver", false);
        reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), null, null, GITLAB_URL, "file", "http://myserver", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), null, null, GITLAB_URL, "file", "http://myserver", false);
        reporter.process(Utils.newIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), null, null, GITLAB_URL, "file", "http://myserver", true);

        Assertions.assertThat(reporter.getIssueCount()).isEqualTo(5);
        Assertions.assertThat(reporter.getNotReportedIssueCount()).isEqualTo(2);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.INFO)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.BLOCKER)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.CRITICAL)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MAJOR)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MINOR)).isEqualTo(1);
        Assertions.assertThat(reporter.getReportIssues()).hasSize(5);
        Assertions.assertThat(reporter.getNotReportedOnDiffReportIssueForSeverity(Severity.MINOR)).hasSize(1);
        Assertions.assertThat(reporter.getNotReportedOnDiffReportIssueForSeverity(Severity.CRITICAL)).hasSize(1);
    }

    @Test
    public void oneIssueNoSast() {
        settings.setProperty(GitLabPlugin.GITLAB_JSON_MODE, JsonMode.NONE.name());

        reporter.process(Utils.newIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver", true);

        Assertions.assertThat(reporter.buildJson()).isEqualTo("[]");
    }

    @Test
    public void oneIssueSast() {
        settings.setProperty(GitLabPlugin.GITLAB_JSON_MODE, JsonMode.SAST.name());

        reporter.process(Utils.newIssue("123", "component", null, 10, Severity.INFO, true, "Issue \"NULL\"", "rule"), null, null, GITLAB_URL, "file", "http://myserver", true);

        Assertions.assertThat(reporter.buildJson()).isEqualTo("[{\"tool\":\"sonarqube\",\"fingerprint\":\"123\",\"message\":\"Issue \\\"NULL\\\"\",\"file\":\"file\",\"line\":\"10\",\"priority\":\"INFO\",\"solution\":\"http://myserver\"}]");
    }

    @Test
    public void oneIssueCodeClimate() {
        settings.setProperty(GitLabPlugin.GITLAB_JSON_MODE, JsonMode.CODECLIMATE.name());

        reporter.process(Utils.newIssue("456", "component", null, 20, Severity.INFO, true, "Issue \"NULL\"", "rule"), null, null, GITLAB_URL, "file", "http://myserver", true);

        Assertions.assertThat(reporter.buildJson()).isEqualTo("[{\"fingerprint\":\"456\",\"check_name\":\"Issue \\\"NULL\\\"\",\"location\":{\"path\":\"file\",\"lines\": { \"begin\":20,\"end\":20}}}]");
    }

    @Test
    public void issuesSast() {
        settings.setProperty(GitLabPlugin.GITLAB_JSON_MODE, JsonMode.SAST.name());

        for (int i = 0; i < 5; i++) {
            reporter.process(Utils.newIssue("toto_" + i, "component", null, null, Severity.INFO, true, "Issue", "rule" + i), null, null, GITLAB_URL, "file", "http://myserver/rule" + i, true);
        }

        Assertions.assertThat(reporter.buildJson()).isEqualTo("[{\"tool\":\"sonarqube\",\"fingerprint\":\"toto_0\",\"message\":\"Issue\",\"file\":\"file\",\"line\":\"0\",\"priority\":\"INFO\",\"solution\":\"http://myserver/rule0\"},{\"tool\":\"sonarqube\",\"fingerprint\":\"toto_1\",\"message\":\"Issue\",\"file\":\"file\",\"line\":\"0\",\"priority\":\"INFO\",\"solution\":\"http://myserver/rule1\"},{\"tool\":\"sonarqube\",\"fingerprint\":\"toto_2\",\"message\":\"Issue\",\"file\":\"file\",\"line\":\"0\",\"priority\":\"INFO\",\"solution\":\"http://myserver/rule2\"},{\"tool\":\"sonarqube\",\"fingerprint\":\"toto_3\",\"message\":\"Issue\",\"file\":\"file\",\"line\":\"0\",\"priority\":\"INFO\",\"solution\":\"http://myserver/rule3\"},{\"tool\":\"sonarqube\",\"fingerprint\":\"toto_4\",\"message\":\"Issue\",\"file\":\"file\",\"line\":\"0\",\"priority\":\"INFO\",\"solution\":\"http://myserver/rule4\"}]");
    }

    @Test
    public void issuesCodeClimate() {
        settings.setProperty(GitLabPlugin.GITLAB_JSON_MODE, JsonMode.CODECLIMATE.name());

        for (int i = 0; i < 5; i++) {
            reporter.process(Utils.newIssue("tata_" + i, "component", null, null, Severity.INFO, true, "Issue", "rule" + i), null, null, GITLAB_URL, "file", "http://myserver/rule" + i, true);
        }

        Assertions.assertThat(reporter.buildJson()).isEqualTo("[{\"fingerprint\":\"tata_0\",\"check_name\":\"Issue\",\"location\":{\"path\":\"file\",\"lines\": { \"begin\":0,\"end\":0}}},{\"fingerprint\":\"tata_1\",\"check_name\":\"Issue\",\"location\":{\"path\":\"file\",\"lines\": { \"begin\":0,\"end\":0}}},{\"fingerprint\":\"tata_2\",\"check_name\":\"Issue\",\"location\":{\"path\":\"file\",\"lines\": { \"begin\":0,\"end\":0}}},{\"fingerprint\":\"tata_3\",\"check_name\":\"Issue\",\"location\":{\"path\":\"file\",\"lines\": { \"begin\":0,\"end\":0}}},{\"fingerprint\":\"tata_4\",\"check_name\":\"Issue\",\"location\":{\"path\":\"file\",\"lines\": { \"begin\":0,\"end\":0}}}]");
    }
}
