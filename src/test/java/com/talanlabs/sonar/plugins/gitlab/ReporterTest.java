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
import org.mockito.Mockito;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.System2;

import javax.annotation.CheckForNull;

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

        config = new GitLabPluginConfiguration(settings, new System2());
        reporter = new Reporter(config);
    }

    private PostJobIssue newMockedIssue(String componentKey, @CheckForNull DefaultInputFile inputFile, @CheckForNull Integer line, Severity severity, boolean isNew, String message, String rule) {
        PostJobIssue issue = Mockito.mock(PostJobIssue.class);
        Mockito.when(issue.inputComponent()).thenReturn(inputFile);
        Mockito.when(issue.componentKey()).thenReturn(componentKey);
        if (line != null) {
            Mockito.when(issue.line()).thenReturn(line);
        }
        Mockito.when(issue.ruleKey()).thenReturn(RuleKey.of("repo", rule));
        Mockito.when(issue.severity()).thenReturn(severity);
        Mockito.when(issue.isNew()).thenReturn(isNew);
        Mockito.when(issue.message()).thenReturn(message);

        return issue;
    }

    @Test
    public void noIssues() {
        Assertions.assertThat(reporter.getIssueCount()).isEqualTo(0);
    }

    @Test
    public void oneIssue() {
        reporter.process(newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), GITLAB_URL, true);

        Assertions.assertThat(reporter.getIssueCount()).isEqualTo(1);
        Assertions.assertThat(reporter.getNotReportedIssueCount()).isEqualTo(0);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.INFO)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.BLOCKER)).isEqualTo(0);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.CRITICAL)).isEqualTo(0);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MAJOR)).isEqualTo(0);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MINOR)).isEqualTo(0);
        Assertions.assertThat(reporter.getReportIssues()).hasSize(1);
        Assertions.assertThat(reporter.getReportIssuesForSeverity(Severity.INFO)).hasSize(1);
        Assertions.assertThat(reporter.getReportIssuesForSeverity(Severity.BLOCKER)).hasSize(0);
        Assertions.assertThat(reporter.getNotReportedOnDiffReportIssues()).hasSize(0);
    }

    @Test
    public void shouldFormatIssuesForMarkdownNoInline() {
        reporter.process(newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), GITLAB_URL, true);
        reporter.process(newMockedIssue("component", null, null, Severity.MINOR, true, "Issue", "rule"), GITLAB_URL, true);
        reporter.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue", "rule"), GITLAB_URL, true);
        reporter.process(newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue", "rule"), GITLAB_URL, true);
        reporter.process(newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue", "rule"), GITLAB_URL, true);

        Assertions.assertThat(reporter.getIssueCount()).isEqualTo(5);
        Assertions.assertThat(reporter.getNotReportedIssueCount()).isEqualTo(0);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.INFO)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.BLOCKER)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.CRITICAL)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MAJOR)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MINOR)).isEqualTo(1);
        Assertions.assertThat(reporter.getReportIssues()).hasSize(5);
        Assertions.assertThat(reporter.getReportIssuesForSeverity(Severity.INFO)).hasSize(1);
        Assertions.assertThat(reporter.getReportIssuesForSeverity(Severity.BLOCKER)).hasSize(1);
        Assertions.assertThat(reporter.getNotReportedOnDiffReportIssues()).hasSize(0);
    }

    @Test
    public void shouldFormatIssuesForMarkdownMixInlineGlobal() {
        reporter.process(newMockedIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), GITLAB_URL, true);
        reporter.process(newMockedIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), GITLAB_URL, false);
        reporter.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), GITLAB_URL, true);
        reporter.process(newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), GITLAB_URL, false);
        reporter.process(newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), GITLAB_URL, true);

        Assertions.assertThat(reporter.getIssueCount()).isEqualTo(5);
        Assertions.assertThat(reporter.getNotReportedIssueCount()).isEqualTo(2);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.INFO)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.BLOCKER)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.CRITICAL)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MAJOR)).isEqualTo(1);
        Assertions.assertThat(reporter.getIssueCountForSeverity(Severity.MINOR)).isEqualTo(1);
        Assertions.assertThat(reporter.getReportIssues()).hasSize(5);
        Assertions.assertThat(reporter.getReportIssuesForSeverity(Severity.INFO)).hasSize(1);
        Assertions.assertThat(reporter.getReportIssuesForSeverity(Severity.BLOCKER)).hasSize(1);
        Assertions.assertThat(reporter.getNotReportedOnDiffReportIssues()).hasSize(2);
        Assertions.assertThat(reporter.getNotReportedOnDiffReportIssueForSeverity(Severity.MINOR)).hasSize(1);
        Assertions.assertThat(reporter.getNotReportedOnDiffReportIssueForSeverity(Severity.CRITICAL)).hasSize(1);
    }
}
