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
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), null, GITLAB_URL, "file", "http://myserver", true, false);

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
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), null, GITLAB_URL, "file", "http://myserver", true, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MINOR, true, "Issue", "rule"), null, GITLAB_URL, "file", "http://myserver", true, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue", "rule"), null, GITLAB_URL, "file", "http://myserver", true, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue", "rule"), null, GITLAB_URL, "file", "http://myserver", true, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue", "rule"), null, GITLAB_URL, "file", "http://myserver", true, false);

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
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), null, GITLAB_URL, "file", "http://myserver", true, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), null, GITLAB_URL, "file", "http://myserver", false, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), null, GITLAB_URL, "file", "http://myserver", true, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), null, GITLAB_URL, "file", "http://myserver", false, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), null, GITLAB_URL, "file", "http://myserver", true, false);

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
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), null, GITLAB_URL, "file", "http://myserver", true, false);

        Assertions.assertThat(reporter.buildSastJson()).isEqualTo("[]");
    }

    @Test
    public void oneIssueSast() {
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), null, GITLAB_URL, "file", "http://myserver", true, true);

        Assertions.assertThat(reporter.buildSastJson()).isEqualTo("[{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"Issue\",\"file\":\"file\",\"line\":\"0\",\"priority\":\"INFO\",\"solution\":\"http://myserver\"}]");
    }

    @Test
    public void issuesSast() {
        for (int i = 0; i < 5; i++) {
            reporter.process(Utils.newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule" + i), null, GITLAB_URL, "file", "http://myserver/rule" + i, true, i % 2 == 0);
        }

        Assertions.assertThat(reporter.buildSastJson()).isEqualTo("[{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"Issue\",\"file\":\"file\",\"line\":\"0\",\"priority\":\"INFO\",\"solution\":\"http://myserver/rule0\"},{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"Issue\",\"file\":\"file\",\"line\":\"0\",\"priority\":\"INFO\",\"solution\":\"http://myserver/rule2\"},{\"tool\":\"sonarqube\",\"fingerprint\":\"null\",\"message\":\"Issue\",\"file\":\"file\",\"line\":\"0\",\"priority\":\"INFO\",\"solution\":\"http://myserver/rule4\"}]");
    }
}
