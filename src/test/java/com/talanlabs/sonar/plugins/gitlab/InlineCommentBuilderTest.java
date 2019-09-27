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

import com.talanlabs.sonar.plugins.gitlab.models.ReportIssue;
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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InlineCommentBuilderTest {

    private static final String GITLAB_URL = "https://gitlab.com/test/test";

    private MapSettings settings;
    private GitLabPluginConfiguration config;

    @Before
    public void setUp() {
        settings = new MapSettings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL")
                .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue("http://localhost:9000").build()).addComponents(GitLabPlugin.definitions()));

        settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "abc123");

        config = new GitLabPluginConfiguration(settings.asConfig(), new System2());
    }

    @Test
    public void testNoIssues() {
        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, Collections.emptyList(), new MarkDownUtils()).buildForMarkdown()).isEqualTo("");
    }

    @Test
    public void testOneIssue() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        ReportIssue r1 = ReportIssue.newBuilder().issue(Utils.newIssue("component", null, 1, Severity.INFO, true, "Issue", "rule")).revision(null).url("lalal").file("file")
                .ruleLink("http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build();

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, Collections.singletonList(r1), new MarkDownUtils()).buildForMarkdown())
                .isEqualTo(":information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
    }

    @Test
    public void testOneIssueAuthor() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);
        settings.setProperty(GitLabPlugin.GITLAB_PING_USER, true);

        ReportIssue r1 = ReportIssue.newBuilder().issue(Utils.newIssue("component", null, 1, Severity.INFO, true, "Issue", "rule")).revision(null).url("lalal").file("file")
                .ruleLink("http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build();

        Assertions.assertThat(new InlineCommentBuilder(config, "123", "john", 1, Collections.singletonList(r1), new MarkDownUtils()).buildForMarkdown())
                .isEqualTo(":information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule) @john");
    }

    @Test
    public void testMultiIssue() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        List<ReportIssue> ris = Stream.iterate(0, i -> i++).limit(10)
                .map(i -> ReportIssue.newBuilder().issue(Utils.newIssue("component", null, 1, Severity.INFO, true, "Issue", "rule")).revision(null).url("lalal").file("file")
                        .ruleLink("http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build()).collect(Collectors.toList());

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, ris, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "* :information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "* :information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "* :information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "* :information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "* :information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "* :information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "* :information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "* :information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "* :information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + "* :information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)");
    }

    @Test
    public void testTemplateConfig() {
        settings.setProperty(GitLabPlugin.GITLAB_PROJECT_ID, "123");
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "123456789");
        settings.setProperty(GitLabPlugin.GITLAB_REF_NAME, "master");

        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, "${projectId}\n${commitSHA[0]}\n${refName}\n${url}\n"
                + "${maxGlobalIssues}\n${maxBlockerIssuesGate}\n${maxCriticalIssuesGate}\n${maxMajorIssuesGate}\n${maxMinorIssuesGate}\n${maxInfoIssuesGate}\n"
                + "${disableIssuesInline?c}\n${onlyIssueFromCommitFile?c}\n${commentNoIssue?c}\n${revision}\n${lineNumber}");

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, Collections.emptyList(), new MarkDownUtils()).buildForMarkdown())
                .isEqualTo("123\n" + "123456789\n" + "master\n" + "https://gitlab.com\n" + "10\n" + "0\n" + "0\n" + "-1\n" + "-1\n" + "-1\n" + "false\n" + "false\n" + "false\n" + "123\n" + "1");
    }

    @Test
    public void testTemplateIssue() {
        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, "${issueCount()}\n<#list issues() as issue>\n${issue.componentKey}\n</#list>");

        List<ReportIssue> ris = Stream.iterate(0, i -> i++).limit(17)
                .map(i -> ReportIssue.newBuilder().issue(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i)).revision(null).url("lalal").file("file")
                        .ruleLink("http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build()).collect(Collectors.toList());

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, ris, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "17\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n"
                        + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n");
    }

    @Test
    public void testTemplateIssueForSeverity() {
        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, "${issueCount(MAJOR)}\n<#list issues(MAJOR) as issue>\n${issue.componentKey}\n</#list>"
                + "${issueCount(\"MAJOR\")}\n<#list issues(\"MAJOR\") as issue>\n${issue.componentKey}\n</#list>");

        List<ReportIssue> ris = Stream.iterate(0, i -> i++).limit(9)
                .map(i -> ReportIssue.newBuilder().issue(Utils.newIssue("component", null, null, i % 2 == 0 ? Severity.MAJOR : Severity.BLOCKER, true, "Issue number:" + i, "rule" + i)).revision(null)
                        .url("lalal").file("file").ruleLink("http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build()).collect(Collectors.toList());

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, ris, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "9\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "9\n" + "component\n"
                        + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n");
    }

    @Test
    public void testTemplateIssueReported() {
        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE,
                "${issueCount(true)}\n<#list issues(true) as issue>\n${issue.componentKey}\n</#list>\n${issueCount(false)}\n<#list issues(false) as issue>\n${issue.componentKey}\n</#list>");

        List<ReportIssue> ris = Stream.iterate(0, i -> i++).limit(17)
                .map(i -> ReportIssue.newBuilder().issue(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i)).revision("123").url("url").file("file")
                        .ruleLink("ruleLink").reportedOnDiff(false).build()).collect(Collectors.toList());

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, ris, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "0\n" + "17\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n"
                        + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n");
    }

    @Test
    public void testTemplateIssueReportedForSeverity() {
        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, "${issueCount(MAJOR,true)}\n<#list issues(MAJOR,true) as issue>\n${issue.componentKey}\n</#list>"
                + "${issueCount(\"MAJOR\",false)}\n<#list issues(\"MAJOR\",false) as issue>\n${issue.componentKey}\n</#list>");

        List<ReportIssue> ris = Stream.iterate(0, i -> i++).limit(10)
                .map(i -> ReportIssue.newBuilder().issue(Utils.newIssue("component", null, null, i % 2 == 0 ? Severity.MAJOR : Severity.BLOCKER, true, "Issue number:" + i, "rule" + i)).revision("123")
                        .url("url").file("file").ruleLink("ruleLink").reportedOnDiff(false).build()).collect(Collectors.toList());

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, ris, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                "0\n" + "10\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n" + "component\n");
    }

    @Test
    public void testTemplateIssueOthers() {
        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE,
                "${emojiSeverity(BLOCKER)}\n${imageSeverity(BLOCKER)}\n${ruleLink(\"repo%3Arule0\")}\n<#list issues() as issue>${print(issue)}\n</#list>");

        List<ReportIssue> ris = Stream.iterate(0, i -> i++).limit(10)
                .map(i -> ReportIssue.newBuilder().issue(Utils.newIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i)).revision(null)
                        .url(GITLAB_URL + "/File.java#L" + i).file("file").ruleLink("http://myserver/coding_rules#rule_key=repo%3Arule" + i).reportedOnDiff(false).build())
                .collect(Collectors.toList());

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, ris, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                ":no_entry:\n" + "![BLOCKER](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/severity-blocker.png)\n"
                        + "http://myserver/coding_rules#rule_key=repo%253Arule0\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                        + ":warning: [Issue number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n");
    }

    @Test
    public void testTemplateIssueFail() {
        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, "<#toto>");

        Assertions.assertThatThrownBy(() -> new InlineCommentBuilder(config, "123", null, 1, Collections.emptyList(), new MarkDownUtils()).buildForMarkdown())
                .isInstanceOf(MessageException.class);
    }
}
