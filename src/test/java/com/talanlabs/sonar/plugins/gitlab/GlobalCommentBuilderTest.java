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

import com.talanlabs.sonar.plugins.gitlab.models.QualityGate;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.System2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GlobalCommentBuilderTest {

    private static final String GITLAB_URL = "https://gitlab.com/test/test";

    private MapSettings settings;
    private GitLabPluginConfiguration config;

    @Before
    public void setUp() {
        settings = new MapSettings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL")
                .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue("http://localhost:9000").build()).addComponents(GitLabPlugin.definitions()));

        settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "123");

        config = new GitLabPluginConfiguration(settings.asConfig(), new System2());
    }

    @Test
    public void testNoIssues() {
        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, new Reporter(config), new MarkDownUtils()).buildForMarkdown())
                .isEqualTo("SonarQube analysis reported no issues.\n");
    }

    @Test
    public void testOneIssue() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        Reporter reporter = new Reporter(config);
        reporter.process(Utils.newIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule", true);

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown())
                .isEqualTo("SonarQube analysis reported 1 issue\n" + "* :information_source: 1 info\n" + "\nWatch the comments in this conversation to review them.\n");
    }

    @Test
    public void testOneIssueOnDir() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        Reporter reporter = new Reporter(config);
        reporter.process(Utils.newIssue("component0", null, null, Severity.INFO, true, "Issue0", "rule0"), null, null, null, "file", "http://myserver/coding_rules#rule_key=repo%3Arule0", false);

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "SonarQube analysis reported 1 issue\n" + "* :information_source: 1 info\n" + "\n"
                        + "Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:\n\n"
                        + "1. :information_source: Issue0 (component0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n");
    }

    @Test
    public void testShouldFormatIssuesForMarkdownNoInline() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        Reporter reporter = new Reporter(config);
        reporter.process(Utils.newIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.MINOR, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.CRITICAL, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.BLOCKER, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule", true);

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "SonarQube analysis reported 5 issues\n" + "* :no_entry: 1 blocker\n" + "* :no_entry_sign: 1 critical\n" + "* :warning: 1 major\n" + "* :arrow_down_small: 1 minor\n"
                        + "* :information_source: 1 info\n" + "\n" + "Watch the comments in this conversation to review them.\n");
    }

    @Test
    public void testShouldFormatIssuesForMarkdownMixInlineGlobal() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        Reporter reporter = new Reporter(config);
        reporter.process(Utils.newIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule0", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule1", false);
        reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule2", true);
        reporter.process(Utils.newIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule3",
                false);
        reporter.process(Utils.newIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule4",
                true);

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "SonarQube analysis reported 5 issues\n" + "* :no_entry: 1 blocker\n" + "* :no_entry_sign: 1 critical\n" + "* :warning: 1 major\n" + "* :arrow_down_small: 1 minor\n"
                        + "* :information_source: 1 info\n" + "\n" + "Watch the comments in this conversation to review them.\n" + "\n" + "#### 2 extra issues\n" + "\n"
                        + "Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:\n\n"
                        + "1. :no_entry_sign: [Issue 3](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                        + "1. :arrow_down_small: [Issue 1](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n");
    }

    @Test
    public void testShouldFormatIssuesForMarkdownWhenInlineCommentsDisabled() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, true);

        Reporter reporter = new Reporter(config);
        reporter.process(Utils.newIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule0", false);
        reporter.process(Utils.newIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule1", false);
        reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule2", false);
        reporter.process(Utils.newIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule3",
                false);
        reporter.process(Utils.newIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule4",
                false);

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "SonarQube analysis reported 5 issues\n" + "* :no_entry: 1 blocker\n" + "* :no_entry_sign: 1 critical\n" + "* :warning: 1 major\n" + "* :arrow_down_small: 1 minor\n"
                        + "* :information_source: 1 info\n" + "\n" + "1. :no_entry: [Issue 4](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
                        + "1. :no_entry_sign: [Issue 3](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                        + "1. :warning: [Issue 2](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
                        + "1. :arrow_down_small: [Issue 1](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
                        + "1. :information_source: [Issue 0](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n");
    }

    @Test
    public void shouldFormatIssuesForMarkdownWhenInlineCommentsDisabledAndLimitReached() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, true);
        settings.setProperty(GitLabPlugin.GITLAB_MAX_GLOBAL_ISSUES, "4");

        Reporter reporter = new Reporter(config);
        reporter.process(Utils.newIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule0", false);
        reporter.process(Utils.newIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule1", false);
        reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule2", false);
        reporter.process(Utils.newIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule3",
                false);
        reporter.process(Utils.newIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule4",
                false);

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "SonarQube analysis reported 5 issues\n" + "* :no_entry: 1 blocker\n" + "* :no_entry_sign: 1 critical\n" + "* :warning: 1 major\n" + "* :arrow_down_small: 1 minor\n"
                        + "* :information_source: 1 info\n" + "\n" + "#### Top 4 issues\n" + "\n"
                        + "1. :no_entry: [Issue 4](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
                        + "1. :no_entry_sign: [Issue 3](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                        + "1. :warning: [Issue 2](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
                        + "1. :arrow_down_small: [Issue 1](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n" + "* ... 1 more\n");
    }

    @Test
    public void testShouldLimitGlobalIssues() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        Reporter reporter = new Reporter(config);
        for (int i = 0; i < 17; i++) {
            reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i), null, null, GITLAB_URL + "/File.java#L" + i, "file",
                    "http://myserver/coding_rules#rule_key=repo%3Arule" + i, false);
        }

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "SonarQube analysis reported 17 issues\n" + "* :warning: 17 major\n" + "\n" + "#### Top 10 extra issues\n" + "\n"
                        + "Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:\n"
                        + "\n" + "1. :warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + "1. :warning: [Issue number:1](https://gitlab.com/test/test/File.java#L1) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
                        + "1. :warning: [Issue number:2](https://gitlab.com/test/test/File.java#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
                        + "1. :warning: [Issue number:3](https://gitlab.com/test/test/File.java#L3) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                        + "1. :warning: [Issue number:4](https://gitlab.com/test/test/File.java#L4) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
                        + "1. :warning: [Issue number:5](https://gitlab.com/test/test/File.java#L5) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule5)\n"
                        + "1. :warning: [Issue number:6](https://gitlab.com/test/test/File.java#L6) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule6)\n"
                        + "1. :warning: [Issue number:7](https://gitlab.com/test/test/File.java#L7) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule7)\n"
                        + "1. :warning: [Issue number:8](https://gitlab.com/test/test/File.java#L8) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule8)\n"
                        + "1. :warning: [Issue number:9](https://gitlab.com/test/test/File.java#L9) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule9)\n" + "* ... 7 more\n");
    }

    @Test
    public void testShouldLimitGlobalIssuesWhenInlineCommentsDisabled() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, true);

        Reporter reporter = new Reporter(config);
        for (int i = 0; i < 17; i++) {
            reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i), null, null, GITLAB_URL + "/File.java#L" + i, "file",
                    "http://myserver/coding_rules#rule_key=repo%3Arule" + i, false);
        }

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "SonarQube analysis reported 17 issues\n" + "* :warning: 17 major\n" + "\n" + "#### Top 10 issues\n" + "\n"
                        + "1. :warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + "1. :warning: [Issue number:1](https://gitlab.com/test/test/File.java#L1) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
                        + "1. :warning: [Issue number:2](https://gitlab.com/test/test/File.java#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
                        + "1. :warning: [Issue number:3](https://gitlab.com/test/test/File.java#L3) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                        + "1. :warning: [Issue number:4](https://gitlab.com/test/test/File.java#L4) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
                        + "1. :warning: [Issue number:5](https://gitlab.com/test/test/File.java#L5) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule5)\n"
                        + "1. :warning: [Issue number:6](https://gitlab.com/test/test/File.java#L6) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule6)\n"
                        + "1. :warning: [Issue number:7](https://gitlab.com/test/test/File.java#L7) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule7)\n"
                        + "1. :warning: [Issue number:8](https://gitlab.com/test/test/File.java#L8) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule8)\n"
                        + "1. :warning: [Issue number:9](https://gitlab.com/test/test/File.java#L9) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule9)\n" + "* ... 7 more\n");
    }

    @Test
    public void testTemplateConfig() {
        settings.setProperty(GitLabPlugin.GITLAB_PROJECT_ID, "123");
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "123456789");
        settings.setProperty(GitLabPlugin.GITLAB_REF_NAME, "master");

        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE, "${projectId}\n${commitSHA[0]}\n${refName}\n${url}\n"
                + "${maxGlobalIssues}\n${maxBlockerIssuesGate}\n${maxCriticalIssuesGate}\n${maxMajorIssuesGate}\n${maxMinorIssuesGate}\n${maxInfoIssuesGate}\n"
                + "${disableIssuesInline?c}\n${onlyIssueFromCommitFile?c}\n${commentNoIssue?c}\n${revision}");

        Reporter reporter = new Reporter(config);

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown())
                .isEqualTo("123\n" + "123456789\n" + "master\n" + "https://gitlab.com\n" + "10\n" + "0\n" + "0\n" + "-1\n" + "-1\n" + "-1\n" + "false\n" + "false\n" + "false\n" + "123456789");
    }

    @Test
    public void testTemplateIssue() {
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE, "${issueCount()}\n<#list issues() as issue>\n${issue.componentKey}\n</#list>");

        Reporter reporter = new Reporter(config);
        for (int i = 0; i < 17; i++) {
            reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i), null, null, GITLAB_URL + "/File.java#L" + i, "file",
                    "http://myserver/coding_rules#rule_key=repo%3Arule", false);
        }

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "17\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n"
                        + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n");
    }

    @Test
    public void testTemplateIssueForSeverity() {
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE, "${issueCount(MAJOR)}\n<#list issues(MAJOR) as issue>\n${issue.componentKey}\n</#list>"
                + "${issueCount(\"MAJOR\")}\n<#list issues(\"MAJOR\") as issue>\n${issue.componentKey}\n</#list>");

        Reporter reporter = new Reporter(config);
        for (int i = 0; i < 17; i++) {
            reporter.process(Utils.newIssue("component", null, null, i % 2 == 0 ? Severity.MAJOR : Severity.BLOCKER, true, "Issue number:" + i, "rule" + i), null, null,
                    GITLAB_URL + "/File.java#L" + i, "file", "http://myserver/coding_rules#rule_key=repo%3Arule", false);
        }

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "9\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "9\n" + "component\n"
                        + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n");
    }

    @Test
    public void testTemplateIssueReported() {
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE,
                "${issueCount(true)}\n<#list issues(true) as issue>\n${issue.componentKey}\n</#list>\n${issueCount(false)}\n<#list issues(false) as issue>\n${issue.componentKey}\n</#list>");

        Reporter reporter = new Reporter(config);
        for (int i = 0; i < 17; i++) {
            reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i), null, null, GITLAB_URL + "/File.java#L" + i, "file",
                    "http://myserver/coding_rules#rule_key=repo%3Arule", false);
        }

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "0\n" + "17\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n"
                        + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n");
    }

    @Test
    public void testTemplateIssueReportedForSeverity() {
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE, "${issueCount(MAJOR,true)}\n<#list issues(MAJOR,true) as issue>\n${issue.componentKey}\n</#list>"
                + "${issueCount(\"MAJOR\",false)}\n<#list issues(\"MAJOR\",false) as issue>\n${issue.componentKey}\n</#list>");

        Reporter reporter = new Reporter(config);
        for (int i = 0; i < 17; i++) {
            reporter.process(Utils.newIssue("component", null, null, i % 2 == 0 ? Severity.MAJOR : Severity.BLOCKER, true, "Issue number:" + i, "rule" + i), null, null,
                    GITLAB_URL + "/File.java#L" + i, "file", "http://myserver/coding_rules#rule_key=repo%3Arule", false);
        }

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown())
                .isEqualTo("0\n" + "9\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n");
    }

    @Test
    public void testTemplateIssueOthers() {
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE,
                "${emojiSeverity(BLOCKER)}\n${imageSeverity(BLOCKER)}\n${ruleLink(\"repo%3Arule0\")}\n<#list issues() as issue>${print(issue)}\n</#list>");

        Reporter reporter = new Reporter(config);
        for (int i = 0; i < 17; i++) {
            reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i), null, null, GITLAB_URL + "/File.java#L" + i, "file",
                    "http://myserver/coding_rules#rule_key=repo%3Arule" + i, false);
        }

        Assertions.assertThat(new GlobalCommentBuilder(config, null, null, reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                ":no_entry:\n" + "![BLOCKER](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-blocker.png)\n"
                        + "http://myserver/coding_rules#rule_key=repo%253Arule0\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:1](https://gitlab.com/test/test/File.java#L1) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
                        + ":warning: [Issue number:2](https://gitlab.com/test/test/File.java#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
                        + ":warning: [Issue number:3](https://gitlab.com/test/test/File.java#L3) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                        + ":warning: [Issue number:4](https://gitlab.com/test/test/File.java#L4) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
                        + ":warning: [Issue number:5](https://gitlab.com/test/test/File.java#L5) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule5)\n"
                        + ":warning: [Issue number:6](https://gitlab.com/test/test/File.java#L6) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule6)\n"
                        + ":warning: [Issue number:7](https://gitlab.com/test/test/File.java#L7) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule7)\n"
                        + ":warning: [Issue number:8](https://gitlab.com/test/test/File.java#L8) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule8)\n"
                        + ":warning: [Issue number:9](https://gitlab.com/test/test/File.java#L9) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule9)\n"
                        + ":warning: [Issue number:10](https://gitlab.com/test/test/File.java#L10) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule10)\n"
                        + ":warning: [Issue number:11](https://gitlab.com/test/test/File.java#L11) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule11)\n"
                        + ":warning: [Issue number:12](https://gitlab.com/test/test/File.java#L12) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule12)\n"
                        + ":warning: [Issue number:13](https://gitlab.com/test/test/File.java#L13) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule13)\n"
                        + ":warning: [Issue number:14](https://gitlab.com/test/test/File.java#L14) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule14)\n"
                        + ":warning: [Issue number:15](https://gitlab.com/test/test/File.java#L15) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule15)\n"
                        + ":warning: [Issue number:16](https://gitlab.com/test/test/File.java#L16) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule16)\n");
    }

    @Test
    public void testTemplateIssueFail() {
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE, "<#toto>");

        Assertions.assertThatThrownBy(() -> new GlobalCommentBuilder(config, null, null, new Reporter(config), new MarkDownUtils()).buildForMarkdown())
                .isInstanceOf(MessageException.class);
    }

    @Test
    public void testQualityGateNoIssues() {
        Assertions.assertThat(
                new GlobalCommentBuilder(config, null, QualityGate.newBuilder().status(QualityGate.Status.OK).conditions(Collections.emptyList()).build(), new Reporter(config), new MarkDownUtils()).buildForMarkdown())
                .isEqualTo("SonarQube analysis indicates that quality gate is passed.\n" + "\n" + "SonarQube analysis reported no issues.\n");
    }

    @Test
    public void testQualityGateOneIssue() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        Reporter reporter = new Reporter(config);
        reporter.process(Utils.newIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), null, null, GITLAB_URL, "file", "http://myserver/coding_rules#rule_key=repo%3Arule", true);

        List<QualityGate.Condition> conditions = new ArrayList<>();
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto1").actual("10").symbol("<").warning("").error("0").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto2").actual("11").symbol(">=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto3").actual("13").symbol("<=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto4").actual("14").symbol(">").warning("20").error("30").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto5").actual("15").symbol("=").warning("10").error("").build());

        Assertions.assertThat(new GlobalCommentBuilder(config, null, QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(conditions).build(), reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "SonarQube analysis indicates that quality gate is warning.\n" + "* Toto1 is passed: Actual value 10\n" + "* Toto2 is passed: Actual value 11\n"
                        + "* Toto3 is passed: Actual value 13\n" + "* Toto4 is warning: Actual value 14 > 20\n" + "* Toto5 is warning: Actual value 15 = 10\n" + "\n"
                        + "SonarQube analysis reported 1 issue\n" + "* :information_source: 1 info\n" + "\n" + "Watch the comments in this conversation to review them.\n");
    }

    @Test
    public void testQualityGateTemplateIssueOthers1() {
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE,
                "${emojiSeverity(BLOCKER)}\n${imageSeverity(BLOCKER)}\n${ruleLink(\"repo%3Arule0\")}\n<#list issues() as issue>${print(issue)}\n</#list>");

        Reporter reporter = new Reporter(config);
        for (int i = 0; i < 17; i++) {
            reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i), null, null, GITLAB_URL + "/File.java#L" + i, "file",
                    "http://myserver/coding_rules#rule_key=repo%3Arule" + i, false);
        }

        List<QualityGate.Condition> conditions = new ArrayList<>();
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto1").actual("10").symbol("<").warning("").error("0").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto2").actual("11").symbol(">=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto3").actual("13").symbol("<=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto4").actual("14").symbol(">").warning("20").error("30").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto5").actual("15").symbol("=").warning("10").error("").build());

        Assertions.assertThat(new GlobalCommentBuilder(config, null, QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(conditions).build(), reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                ":no_entry:\n" + "![BLOCKER](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-blocker.png)\n"
                        + "http://myserver/coding_rules#rule_key=repo%253Arule0\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:1](https://gitlab.com/test/test/File.java#L1) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
                        + ":warning: [Issue number:2](https://gitlab.com/test/test/File.java#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
                        + ":warning: [Issue number:3](https://gitlab.com/test/test/File.java#L3) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                        + ":warning: [Issue number:4](https://gitlab.com/test/test/File.java#L4) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
                        + ":warning: [Issue number:5](https://gitlab.com/test/test/File.java#L5) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule5)\n"
                        + ":warning: [Issue number:6](https://gitlab.com/test/test/File.java#L6) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule6)\n"
                        + ":warning: [Issue number:7](https://gitlab.com/test/test/File.java#L7) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule7)\n"
                        + ":warning: [Issue number:8](https://gitlab.com/test/test/File.java#L8) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule8)\n"
                        + ":warning: [Issue number:9](https://gitlab.com/test/test/File.java#L9) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule9)\n"
                        + ":warning: [Issue number:10](https://gitlab.com/test/test/File.java#L10) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule10)\n"
                        + ":warning: [Issue number:11](https://gitlab.com/test/test/File.java#L11) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule11)\n"
                        + ":warning: [Issue number:12](https://gitlab.com/test/test/File.java#L12) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule12)\n"
                        + ":warning: [Issue number:13](https://gitlab.com/test/test/File.java#L13) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule13)\n"
                        + ":warning: [Issue number:14](https://gitlab.com/test/test/File.java#L14) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule14)\n"
                        + ":warning: [Issue number:15](https://gitlab.com/test/test/File.java#L15) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule15)\n"
                        + ":warning: [Issue number:16](https://gitlab.com/test/test/File.java#L16) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule16)\n");
    }

    @Test
    public void testQualityGateTemplateIssueOthers2() {
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE,
                "${qualityGate.status}\n${emojiSeverity(BLOCKER)}\n${imageSeverity(BLOCKER)}\n${ruleLink(\"repo%3Arule0\")}\n<#list issues() as issue>${print(issue)}\n</#list>");

        Reporter reporter = new Reporter(config);
        for (int i = 0; i < 17; i++) {
            reporter.process(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i), null, null, GITLAB_URL + "/File.java#L" + i, "file",
                    "http://myserver/coding_rules#rule_key=repo%3Arule" + i, false);
        }

        List<QualityGate.Condition> conditions = new ArrayList<>();
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto1").actual("10").symbol("<").warning("").error("0").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto2").actual("11").symbol(">=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto3").actual("13").symbol("<=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto4").actual("14").symbol(">").warning("20").error("30").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto5").actual("15").symbol("=").warning("10").error("").build());

        Assertions.assertThat(new GlobalCommentBuilder(config, null, QualityGate.newBuilder().status(QualityGate.Status.WARN).conditions(conditions).build(), reporter, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "WARN\n" + ":no_entry:\n" + "![BLOCKER](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-blocker.png)\n"
                        + "http://myserver/coding_rules#rule_key=repo%253Arule0\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:1](https://gitlab.com/test/test/File.java#L1) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
                        + ":warning: [Issue number:2](https://gitlab.com/test/test/File.java#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
                        + ":warning: [Issue number:3](https://gitlab.com/test/test/File.java#L3) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                        + ":warning: [Issue number:4](https://gitlab.com/test/test/File.java#L4) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
                        + ":warning: [Issue number:5](https://gitlab.com/test/test/File.java#L5) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule5)\n"
                        + ":warning: [Issue number:6](https://gitlab.com/test/test/File.java#L6) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule6)\n"
                        + ":warning: [Issue number:7](https://gitlab.com/test/test/File.java#L7) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule7)\n"
                        + ":warning: [Issue number:8](https://gitlab.com/test/test/File.java#L8) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule8)\n"
                        + ":warning: [Issue number:9](https://gitlab.com/test/test/File.java#L9) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule9)\n"
                        + ":warning: [Issue number:10](https://gitlab.com/test/test/File.java#L10) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule10)\n"
                        + ":warning: [Issue number:11](https://gitlab.com/test/test/File.java#L11) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule11)\n"
                        + ":warning: [Issue number:12](https://gitlab.com/test/test/File.java#L12) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule12)\n"
                        + ":warning: [Issue number:13](https://gitlab.com/test/test/File.java#L13) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule13)\n"
                        + ":warning: [Issue number:14](https://gitlab.com/test/test/File.java#L14) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule14)\n"
                        + ":warning: [Issue number:15](https://gitlab.com/test/test/File.java#L15) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule15)\n"
                        + ":warning: [Issue number:16](https://gitlab.com/test/test/File.java#L16) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule16)\n");
    }
}
