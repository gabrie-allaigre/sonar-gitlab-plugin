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

public class TemplateTest {

    private static final String GITLAB_URL = "https://gitlab.com/test/test";

    private static final String TEMPLATE = "<#assign newIssueCount = issueCount() notReportedIssueCount = issueCount(false)>\n" +
            "<#assign hasInlineIssues = newIssueCount gt notReportedIssueCount extraIssuesTruncated = notReportedIssueCount gt maxGlobalIssues>\n" +
            "<#if newIssueCount == 0>\n" +
            "SonarQube analysis reported no issues.\n" +
            "<#else>\n" +
            "SonarQube analysis reported ${newIssueCount} issue<#if newIssueCount gt 1>s</#if>\n" +
            "    <#assign newIssuesBlocker = issueCount(BLOCKER) newIssuesCritical = issueCount(CRITICAL) newIssuesMajor = issueCount(MAJOR) newIssuesMinor = issueCount(MINOR) newIssuesInfo = issueCount(INFO)>\n" +
            "    <#if newIssuesBlocker gt 0>\n" +
            "* ${emojiSeverity(BLOCKER)} ${newIssuesBlocker} blocker\n" +
            "    </#if>\n" +
            "    <#if newIssuesCritical gt 0>\n" +
            "* ${emojiSeverity(CRITICAL)} ${newIssuesCritical} critical\n" +
            "    </#if>\n" +
            "    <#if newIssuesMajor gt 0>\n" +
            "* ${emojiSeverity(MAJOR)} ${newIssuesMajor} major\n" +
            "    </#if>\n" +
            "    <#if newIssuesMinor gt 0>\n" +
            "* ${emojiSeverity(MINOR)} ${newIssuesMinor} minor\n" +
            "    </#if>\n" +
            "    <#if newIssuesInfo gt 0>\n" +
            "* ${emojiSeverity(INFO)} ${newIssuesInfo} info\n" +
            "    </#if>\n" +
            "    <#if !disableIssuesInline && hasInlineIssues>\n" +
            "\n" +
            "Watch the comments in this conversation to review them.\n" +
            "    </#if>\n" +
            "    <#if notReportedIssueCount gt 0>\n" +
            "        <#if !disableIssuesInline>\n" +
            "            <#if hasInlineIssues || extraIssuesTruncated>\n" +
            "                <#if notReportedIssueCount <= maxGlobalIssues>\n" +
            "\n" +
            "#### ${notReportedIssueCount} extra issue<#if notReportedIssueCount gt 1>s</#if>\n" +
            "                <#else>\n" +
            "\n" +
            "#### Top ${maxGlobalIssues} extra issue<#if maxGlobalIssues gt 1>s</#if>\n" +
            "                </#if>\n" +
            "            </#if>\n" +
            "\n" +
            "Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:\n" +
            "        <#elseif extraIssuesTruncated>\n" +
            "\n" +
            "#### Top ${maxGlobalIssues} issue<#if maxGlobalIssues gt 1>s</#if>\n" +
            "        </#if>\n" +
            "\n" +
            "        <#assign reportedIssueCount = 0>\n" +
            "        <#list issues(false) as issue>\n" +
            "            <#if reportedIssueCount < maxGlobalIssues>\n" +
            "1. ${print(issue)}\n" +
            "            </#if>\n" +
            "            <#assign reportedIssueCount++>\n" +
            "        </#list>\n" +
            "        <#if notReportedIssueCount gt maxGlobalIssues>\n" +
            "* ... ${notReportedIssueCount-maxGlobalIssues} more\n" +
            "        </#if>\n" +
            "    </#if>\n" +
            "</#if>";

    private Settings settings;
    private GitLabPluginConfiguration config;

    @Before
    public void setUp() {
        settings = new Settings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL")
                .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE).build()).addComponents(GitLabPlugin.definitions()));

        settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");

        config = new GitLabPluginConfiguration(settings, new System2());

        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE, TEMPLATE);
    }

    @Test
    public void testNoIssues() {
        Assertions.assertThat(new GlobalCommentBuilder(config, new Reporter(config), new MarkDownUtils(settings)).buildForMarkdown()).isEqualTo("SonarQube analysis reported no issues.\n");
    }

    @Test
    public void testOneIssue() {
        Reporter reporter = new Reporter(config);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), GITLAB_URL, true);

        Assertions.assertThat(new GlobalCommentBuilder(config, reporter, new MarkDownUtils(settings)).buildForMarkdown())
                .isEqualTo("SonarQube analysis reported 1 issue\n" + "* :information_source: 1 info\n" + "\nWatch the comments in this conversation to review them.\n");
    }

    @Test
    public void testOneIssueOnDir() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        Reporter reporter = new Reporter(config);
        reporter.process(Utils.newMockedIssue("component0", null, null, Severity.INFO, true, "Issue0", "rule0"), null, false);

        Assertions.assertThat(new GlobalCommentBuilder(config, reporter, new MarkDownUtils(settings)).buildForMarkdown()).isEqualTo(
                "SonarQube analysis reported 1 issue\n" + "* :information_source: 1 info\n" + "\n"
                        + "Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:\n\n"
                        + "1. :information_source: Issue0 (component0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n");
    }

    @Test
    public void testShouldFormatIssuesForMarkdownNoInline() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        Reporter reporter = new Reporter(config);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), GITLAB_URL, true);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MINOR, true, "Issue", "rule"), GITLAB_URL, true);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue", "rule"), GITLAB_URL, true);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue", "rule"), GITLAB_URL, true);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue", "rule"), GITLAB_URL, true);

        Assertions.assertThat(new GlobalCommentBuilder(config, reporter, new MarkDownUtils(settings)).buildForMarkdown()).isEqualTo(
                "SonarQube analysis reported 5 issues\n" + "* :no_entry: 1 blocker\n" + "* :no_entry_sign: 1 critical\n" + "* :warning: 1 major\n" + "* :arrow_down_small: 1 minor\n"
                        + "* :information_source: 1 info\n" + "\n" + "Watch the comments in this conversation to review them.\n");
    }

    @Test
    public void testShouldFormatIssuesForMarkdownMixInlineGlobal() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        Reporter reporter = new Reporter(config);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), GITLAB_URL, true);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), GITLAB_URL, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), GITLAB_URL, true);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), GITLAB_URL, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), GITLAB_URL, true);

        Assertions.assertThat(new GlobalCommentBuilder(config, reporter, new MarkDownUtils(settings)).buildForMarkdown()).isEqualTo(
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
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), GITLAB_URL, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), GITLAB_URL, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), GITLAB_URL, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), GITLAB_URL, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), GITLAB_URL, false);

        Assertions.assertThat(new GlobalCommentBuilder(config, reporter, new MarkDownUtils(settings)).buildForMarkdown()).isEqualTo(
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
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), GITLAB_URL, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), GITLAB_URL, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), GITLAB_URL, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), GITLAB_URL, false);
        reporter.process(Utils.newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), GITLAB_URL, false);

        Assertions.assertThat(new GlobalCommentBuilder(config, reporter, new MarkDownUtils(settings)).buildForMarkdown()).isEqualTo(
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
            reporter.process(Utils.newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i), GITLAB_URL + "/File.java#L" + i, false);
        }

        Assertions.assertThat(new GlobalCommentBuilder(config, reporter, new MarkDownUtils(settings)).buildForMarkdown()).isEqualTo(
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
            reporter.process(Utils.newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i), GITLAB_URL + "/File.java#L" + i, false);
        }

        Assertions.assertThat(new GlobalCommentBuilder(config, reporter, new MarkDownUtils(settings)).buildForMarkdown()).isEqualTo(
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
    public void testOtherTemplate() {
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE, "<#assign newIssueCount = issueCount() notReportedIssueCount = issueCount(false)>\n" +
                "<#assign hasInlineIssues = newIssueCount gt notReportedIssueCount extraIssuesTruncated = notReportedIssueCount gt maxGlobalIssues>\n" +
                "<#if newIssueCount == 0>\n" +
                "SonarQube analysis reported no issues.\n" +
                "<#else>\n" +
                "SonarQube analysis reported ${newIssueCount} issue<#if newIssueCount gt 1>s</#if>\n" +
                "    <#assign newIssuesBlocker = issueCount(BLOCKER) newIssuesCritical = issueCount(CRITICAL) newIssuesMajor = issueCount(MAJOR) newIssuesMinor = issueCount(MINOR) newIssuesInfo = issueCount(INFO)>\n" +
                "    <#if newIssuesBlocker gt 0>\n" +
                "* ${imageSeverity(BLOCKER)} ${newIssuesBlocker} blocker\n" +
                "    </#if>\n" +
                "    <#if newIssuesCritical gt 0>\n" +
                "* ${imageSeverity(CRITICAL)} ${newIssuesCritical} critical\n" +
                "    </#if>\n" +
                "    <#if newIssuesMajor gt 0>\n" +
                "* ${imageSeverity(MAJOR)} ${newIssuesMajor} major\n" +
                "    </#if>\n" +
                "    <#if newIssuesMinor gt 0>\n" +
                "* ${imageSeverity(MINOR)} ${newIssuesMinor} minor\n" +
                "    </#if>\n" +
                "    <#if newIssuesInfo gt 0>\n" +
                "* ${imageSeverity(INFO)} ${newIssuesInfo} info\n" +
                "    </#if>\n" +
                "    <#if !disableIssuesInline && hasInlineIssues>\n" +
                "\n" +
                "Watch the comments in this conversation to review them.\n" +
                "    </#if>\n" +
                "    <#if notReportedIssueCount gt 0>\n" +
                "        <#if !disableIssuesInline>\n" +
                "            <#if hasInlineIssues || extraIssuesTruncated>\n" +
                "                <#if notReportedIssueCount <= maxGlobalIssues>\n" +
                "\n" +
                "#### ${notReportedIssueCount} extra issue<#if notReportedIssueCount gt 1>s</#if>\n" +
                "                <#else>\n" +
                "\n" +
                "#### Top ${maxGlobalIssues} extra issue<#if maxGlobalIssues gt 1>s</#if>\n" +
                "                </#if>\n" +
                "            </#if>\n" +
                "\n" +
                "Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:\n" +
                "        <#elseif extraIssuesTruncated>\n" +
                "\n" +
                "#### Top ${maxGlobalIssues} issue<#if maxGlobalIssues gt 1>s</#if>\n" +
                "        </#if>\n" +
                "\n" +
                "        <#assign reportedIssueCount = 0>\n" +
                "        <#list issues(false) as issue>\n" +
                "            <#if reportedIssueCount < maxGlobalIssues>\n" +
                "1. <@p issue=issue/>\n" +
                "            </#if>\n" +
                "            <#assign reportedIssueCount++>\n" +
                "        </#list>\n" +
                "        <#if notReportedIssueCount gt maxGlobalIssues>\n" +
                "* ... ${notReportedIssueCount-maxGlobalIssues} more\n" +
                "        </#if>\n" +
                "    </#if>\n" +
                "</#if>\n" +
                "<#macro p issue>\n" +
                "${imageSeverity(issue.severity)} <#if issue.url??>[${issue.message}](${issue.url})<#else>${issue.message}<#if issue.componentKey??>${issue.componentKey}</#if></#if> [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](${ruleLink(issue.ruleKey)})" +
                "</#macro>");

        Reporter reporter = new Reporter(config);
        for (int i = 0; i < 17; i++) {
            reporter.process(Utils.newMockedIssue("component", null, null, i % 2 == 0 ? Severity.MAJOR : Severity.MINOR, true, "Issue number:" + i, "rule" + i), GITLAB_URL + "/File.java#L" + i, i % 3 == 0);
        }

        Assertions.assertThat(new GlobalCommentBuilder(config, reporter, new MarkDownUtils(settings)).buildForMarkdown()).isEqualTo("SonarQube analysis reported 17 issues\n" +
                "* ![MAJOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-major.png) 9 major\n" +
                "* ![MINOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-minor.png) 8 minor\n" +
                "\n" +
                "Watch the comments in this conversation to review them.\n" +
                "\n" +
                "#### Top 10 extra issues\n" +
                "\n" +
                "Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:\n" +
                "\n" +
                "1. ![MAJOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-major.png) [Issue number:2](https://gitlab.com/test/test/File.java#L2) [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule2)\n" +
                "1. ![MAJOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-major.png) [Issue number:4](https://gitlab.com/test/test/File.java#L4) [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule4)\n" +
                "1. ![MAJOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-major.png) [Issue number:8](https://gitlab.com/test/test/File.java#L8) [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule8)\n" +
                "1. ![MAJOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-major.png) [Issue number:10](https://gitlab.com/test/test/File.java#L10) [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule10)\n" +
                "1. ![MAJOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-major.png) [Issue number:14](https://gitlab.com/test/test/File.java#L14) [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule14)\n" +
                "1. ![MAJOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-major.png) [Issue number:16](https://gitlab.com/test/test/File.java#L16) [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule16)\n" +
                "1. ![MINOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-minor.png) [Issue number:1](https://gitlab.com/test/test/File.java#L1) [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule1)\n" +
                "1. ![MINOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-minor.png) [Issue number:5](https://gitlab.com/test/test/File.java#L5) [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule5)\n" +
                "1. ![MINOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-minor.png) [Issue number:7](https://gitlab.com/test/test/File.java#L7) [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule7)\n" +
                "1. ![MINOR](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-minor.png) [Issue number:11](https://gitlab.com/test/test/File.java#L11) [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule11)\n" +
                "* ... 1 more\n");
    }
}
