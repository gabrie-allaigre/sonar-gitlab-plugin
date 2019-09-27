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
import org.sonar.api.utils.System2;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InlineTemplateTest {

    private static final String TEMPLATE = "<#list issues() as issue>\n" + "<@p issue=issue/>\n" + "</#list>\n" + "<#macro p issue>\n"
            + "${emojiSeverity(issue.severity)} ${issue.message} [:blue_book:](${ruleLink(issue.ruleKey)})\n" + "</#macro>";

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

        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, TEMPLATE);
    }

    @Test
    public void testOneIssue() {
        ReportIssue r1 =ReportIssue.newBuilder().issue(Utils.newIssue("component", null, 1, Severity.INFO, true, "Issue", "rule")).revision(null).url("lalal").file("file").ruleLink(
                "http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build();

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, Collections.singletonList(r1), new MarkDownUtils()).buildForMarkdown())
                .isEqualTo(":information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n");
    }

    @Test
    public void testTwoIssue() {
        List<ReportIssue> ris = Stream.iterate(0, i -> i++).limit(2)
                .map(i ->ReportIssue.newBuilder().issue(Utils.newIssue("component", null, 1, Severity.INFO, true, "Issue", "rule")).revision(null).url("lalal").file("file").ruleLink(
                        "http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build()).collect(Collectors.toList());

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, ris, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                ":information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + ":information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n");
    }

    @Test
    public void testUnescapeHTML() {
        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, TEMPLATE + "&agrave;&acirc;&eacute;&ccedil;");

        ReportIssue r1 =ReportIssue.newBuilder().issue(Utils.newIssue("component", null, 1, Severity.INFO, true, "Issue", "rule")).revision(null).url("lalal").file("file").ruleLink(
                "http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build();

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, Collections.singletonList(r1), new MarkDownUtils()).buildForMarkdown())
                .isEqualTo(":information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\nàâéç");
    }
}
