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

public class GlobalReportTest {

    private static final String GITLAB_URL = "https://gitlab.com/test/test";

    private Settings settings;
    private GitLabPluginConfiguration config;

    @Before
    public void setup() {
        settings = new Settings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL")
                .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE).build()).addComponents(GitLabPlugin.definitions()));

        settings.setProperty("sonar.host.url", "http://myserver");

        config = new GitLabPluginConfiguration(settings, new System2());
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
        GlobalReport globalReport = new GlobalReport(config, new MarkDownUtils(settings));

        String desiredMarkdown = "SonarQube analysis reported no issues.";

        String formattedGlobalReport = globalReport.formatForMarkdown();

        Assertions.assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
    }

    @Test
    public void oneIssue() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        GlobalReport globalReport = new GlobalReport(config, new MarkDownUtils(settings));
        globalReport.process(newMockedIssue("component", null, null, Severity.INFO, true, "IssueUrl", "rule"), GITLAB_URL, true);

        Assertions.assertThat(globalReport.formatForMarkdown())
                .isEqualTo("SonarQube analysis reported 1 postJobIssue\n" + "* :information_source: 1 info\n" + "\nWatch the comments in this conversation to review them.\n");
    }

    @Test
    public void oneIssueOnDir() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        GlobalReport globalReport = new GlobalReport(config, new MarkDownUtils(settings));
        globalReport.process(newMockedIssue("component0", null, null, Severity.INFO, true, "Issue0", "rule0"), null, false);

        Assertions.assertThat(globalReport.formatForMarkdown()).isEqualTo("SonarQube analysis reported 1 postJobIssue\n" + "* :information_source: 1 info\n" + "\n"
                + "Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:\n\n"
                + "1. :information_source: Issue0 (component0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n");
    }

    @Test
    public void shouldFormatIssuesForMarkdownNoInline() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        GlobalReport globalReport = new GlobalReport(config, new MarkDownUtils(settings));
        globalReport.process(newMockedIssue("component", null, null, Severity.INFO, true, "IssueUrl", "rule"), GITLAB_URL, true);
        globalReport.process(newMockedIssue("component", null, null, Severity.MINOR, true, "IssueUrl", "rule"), GITLAB_URL, true);
        globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "IssueUrl", "rule"), GITLAB_URL, true);
        globalReport.process(newMockedIssue("component", null, null, Severity.CRITICAL, true, "IssueUrl", "rule"), GITLAB_URL, true);
        globalReport.process(newMockedIssue("component", null, null, Severity.BLOCKER, true, "IssueUrl", "rule"), GITLAB_URL, true);

        Assertions.assertThat(globalReport.formatForMarkdown()).isEqualTo(
                "SonarQube analysis reported 5 issues\n" + "* :no_entry: 1 blocker\n" + "* :no_entry_sign: 1 critical\n" + "* :warning: 1 major\n" + "* :arrow_down_small: 1 minor\n"
                        + "* :information_source: 1 info\n" + "\n" + "Watch the comments in this conversation to review them.\n");
    }

    @Test
    public void shouldFormatIssuesForMarkdownMixInlineGlobal() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        GlobalReport globalReport = new GlobalReport(config, new MarkDownUtils(settings));
        globalReport.process(newMockedIssue("component", null, null, Severity.INFO, true, "IssueUrl 0", "rule0"), GITLAB_URL, true);
        globalReport.process(newMockedIssue("component", null, null, Severity.MINOR, true, "IssueUrl 1", "rule1"), GITLAB_URL, false);
        globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "IssueUrl 2", "rule2"), GITLAB_URL, true);
        globalReport.process(newMockedIssue("component", null, null, Severity.CRITICAL, true, "IssueUrl 3", "rule3"), GITLAB_URL, false);
        globalReport.process(newMockedIssue("component", null, null, Severity.BLOCKER, true, "IssueUrl 4", "rule4"), GITLAB_URL, true);

        Assertions.assertThat(globalReport.formatForMarkdown()).isEqualTo(
                "SonarQube analysis reported 5 issues\n" + "* :no_entry: 1 blocker\n" + "* :no_entry_sign: 1 critical\n" + "* :warning: 1 major\n" + "* :arrow_down_small: 1 minor\n"
                        + "* :information_source: 1 info\n" + "\n" + "Watch the comments in this conversation to review them.\n" + "\n" + "#### 2 extra issues\n" + "\n"
                        + "Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:\n\n"
                        + "1. :no_entry_sign: [IssueUrl 3](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                        + "1. :arrow_down_small: [IssueUrl 1](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n");
    }

    @Test
    public void shouldFormatIssuesForMarkdownWhenInlineCommentsDisabled() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, true);

        GlobalReport globalReport = new GlobalReport(config, new MarkDownUtils(settings));
        globalReport.process(newMockedIssue("component", null, null, Severity.INFO, true, "IssueUrl 0", "rule0"), GITLAB_URL, false);
        globalReport.process(newMockedIssue("component", null, null, Severity.MINOR, true, "IssueUrl 1", "rule1"), GITLAB_URL, false);
        globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "IssueUrl 2", "rule2"), GITLAB_URL, false);
        globalReport.process(newMockedIssue("component", null, null, Severity.CRITICAL, true, "IssueUrl 3", "rule3"), GITLAB_URL, false);
        globalReport.process(newMockedIssue("component", null, null, Severity.BLOCKER, true, "IssueUrl 4", "rule4"), GITLAB_URL, false);

        Assertions.assertThat(globalReport.formatForMarkdown()).isEqualTo(
                "SonarQube analysis reported 5 issues\n" + "* :no_entry: 1 blocker\n" + "* :no_entry_sign: 1 critical\n" + "* :warning: 1 major\n" + "* :arrow_down_small: 1 minor\n"
                        + "* :information_source: 1 info\n" + "\n" + "1. :no_entry: [IssueUrl 4](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
                        + "1. :no_entry_sign: [IssueUrl 3](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                        + "1. :warning: [IssueUrl 2](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
                        + "1. :arrow_down_small: [IssueUrl 1](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
                        + "1. :information_source: [IssueUrl 0](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n");
    }

    @Test
    public void shouldFormatIssuesForMarkdownWhenInlineCommentsDisabledAndLimitReached() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, true);
        settings.setProperty(GitLabPlugin.GITLAB_MAX_GLOBAL_ISSUES, "4");

        GlobalReport globalReport = new GlobalReport(config, new MarkDownUtils(settings));

        globalReport.process(newMockedIssue("component", null, null, Severity.INFO, true, "IssueUrl 0", "rule0"), GITLAB_URL, false);
        globalReport.process(newMockedIssue("component", null, null, Severity.MINOR, true, "IssueUrl 1", "rule1"), GITLAB_URL, false);
        globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "IssueUrl 2", "rule2"), GITLAB_URL, false);
        globalReport.process(newMockedIssue("component", null, null, Severity.CRITICAL, true, "IssueUrl 3", "rule3"), GITLAB_URL, false);
        globalReport.process(newMockedIssue("component", null, null, Severity.BLOCKER, true, "IssueUrl 4", "rule4"), GITLAB_URL, false);

        Assertions.assertThat(globalReport.formatForMarkdown()).isEqualTo(
                "SonarQube analysis reported 5 issues\n" + "* :no_entry: 1 blocker\n" + "* :no_entry_sign: 1 critical\n" + "* :warning: 1 major\n" + "* :arrow_down_small: 1 minor\n"
                        + "* :information_source: 1 info\n" + "\n" + "#### Top 4 issues\n" + "\n"
                        + "1. :no_entry: [IssueUrl 4](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
                        + "1. :no_entry_sign: [IssueUrl 3](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                        + "1. :warning: [IssueUrl 2](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
                        + "1. :arrow_down_small: [IssueUrl 1](https://gitlab.com/test/test) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n" + "* ... 1 more\n");
    }

    @Test
    public void shouldLimitGlobalIssues() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, false);

        GlobalReport globalReport = new GlobalReport(config, new MarkDownUtils(settings));
        for (int i = 0; i < 17; i++) {
            globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "IssueUrl number:" + i, "rule" + i), GITLAB_URL + "/File.java#L" + i, false);
        }

        Assertions.assertThat(globalReport.formatForMarkdown()).isEqualTo("SonarQube analysis reported 17 issues\n" + "* :warning: 17 major\n" + "\n" + "#### Top 10 extra issues\n" + "\n"
                + "Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:\n" + "\n"
                + "1. :warning: [IssueUrl number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                + "1. :warning: [IssueUrl number:1](https://gitlab.com/test/test/File.java#L1) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
                + "1. :warning: [IssueUrl number:2](https://gitlab.com/test/test/File.java#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
                + "1. :warning: [IssueUrl number:3](https://gitlab.com/test/test/File.java#L3) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                + "1. :warning: [IssueUrl number:4](https://gitlab.com/test/test/File.java#L4) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
                + "1. :warning: [IssueUrl number:5](https://gitlab.com/test/test/File.java#L5) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule5)\n"
                + "1. :warning: [IssueUrl number:6](https://gitlab.com/test/test/File.java#L6) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule6)\n"
                + "1. :warning: [IssueUrl number:7](https://gitlab.com/test/test/File.java#L7) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule7)\n"
                + "1. :warning: [IssueUrl number:8](https://gitlab.com/test/test/File.java#L8) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule8)\n"
                + "1. :warning: [IssueUrl number:9](https://gitlab.com/test/test/File.java#L9) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule9)\n" + "* ... 7 more\n");
    }

    @Test
    public void shouldLimitGlobalIssuesWhenInlineCommentsDisabled() {
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, true);

        GlobalReport globalReport = new GlobalReport(config, new MarkDownUtils(settings));
        for (int i = 0; i < 17; i++) {
            globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "IssueUrl number:" + i, "rule" + i), GITLAB_URL + "/File.java#L" + i, false);
        }

        Assertions.assertThat(globalReport.formatForMarkdown()).isEqualTo("SonarQube analysis reported 17 issues\n" + "* :warning: 17 major\n" + "\n" + "#### Top 10 issues\n" + "\n"
                + "1. :warning: [IssueUrl number:0](https://gitlab.com/test/test/File.java#L0) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
                + "1. :warning: [IssueUrl number:1](https://gitlab.com/test/test/File.java#L1) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
                + "1. :warning: [IssueUrl number:2](https://gitlab.com/test/test/File.java#L2) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
                + "1. :warning: [IssueUrl number:3](https://gitlab.com/test/test/File.java#L3) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
                + "1. :warning: [IssueUrl number:4](https://gitlab.com/test/test/File.java#L4) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
                + "1. :warning: [IssueUrl number:5](https://gitlab.com/test/test/File.java#L5) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule5)\n"
                + "1. :warning: [IssueUrl number:6](https://gitlab.com/test/test/File.java#L6) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule6)\n"
                + "1. :warning: [IssueUrl number:7](https://gitlab.com/test/test/File.java#L7) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule7)\n"
                + "1. :warning: [IssueUrl number:8](https://gitlab.com/test/test/File.java#L8) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule8)\n"
                + "1. :warning: [IssueUrl number:9](https://gitlab.com/test/test/File.java#L9) [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule9)\n" + "* ... 7 more\n");
    }
}
