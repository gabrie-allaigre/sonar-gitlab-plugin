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

import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;

import java.util.List;
import java.util.Locale;

public class GlobalCommentBuilder {

    private final GitLabPluginConfiguration gitLabPluginConfiguration;
    private final Reporter reporter;
    private final MarkDownUtils markDownUtils;

    public GlobalCommentBuilder(GitLabPluginConfiguration gitLabPluginConfiguration, Reporter reporter, MarkDownUtils markDownUtils) {
        super();

        this.gitLabPluginConfiguration = gitLabPluginConfiguration;
        this.reporter = reporter;
        this.markDownUtils = markDownUtils;
    }

    public String buildForMarkdown() {
        StringBuilder sb = new StringBuilder();

        int newIssues = reporter.getIssueCount();
        if (newIssues == 0) {
            return "SonarQube analysis reported no issues.";
        }

        boolean hasInlineIssues = newIssues > reporter.getNotReportedIssueCount();
        boolean extraIssuesTruncated = reporter.getNotReportedIssueCount() > gitLabPluginConfiguration.maxGlobalIssues();
        sb.append("SonarQube analysis reported ").append(newIssues).append(" issue").append(newIssues > 1 ? "s" : "").append("\n");

        appendSummaryBySeverity(sb);

        if (gitLabPluginConfiguration.tryReportIssuesInline() && hasInlineIssues) {
            sb.append("\nWatch the comments in this conversation to review them.\n");
        }

        if (reporter.getNotReportedIssueCount() > 0) {
            appendExtraIssues(sb, hasInlineIssues, extraIssuesTruncated);
        }

        return sb.toString();
    }

    private void appendSummaryBySeverity(StringBuilder sb) {
        Reporter.SEVERITIES.forEach(severity -> appendNewIssues(sb, severity));
    }

    private void appendNewIssues(StringBuilder builder, Severity severity) {
        int issueCount = reporter.getIssueCountForSeverity(severity);
        if (issueCount > 0) {
            builder.append("* ").append(markDownUtils.getEmojiForSeverity(severity)).append(" ").append(issueCount).append(" ").append(severity.name().toLowerCase(Locale.ENGLISH)).append("\n");
        }
    }

    private void appendExtraIssues(StringBuilder builder, boolean hasInlineIssues, boolean extraIssuesTruncated) {
        if (gitLabPluginConfiguration.tryReportIssuesInline()) {
            if (hasInlineIssues || extraIssuesTruncated) {
                int extraCount;
                builder.append("\n#### ");
                if (reporter.getNotReportedIssueCount() <= gitLabPluginConfiguration.maxGlobalIssues()) {
                    extraCount = reporter.getNotReportedIssueCount();
                } else {
                    extraCount = gitLabPluginConfiguration.maxGlobalIssues();
                    builder.append("Top ");
                }
                builder.append(extraCount).append(" extra issue").append(extraCount > 1 ? "s" : "").append("\n");
            }
            builder.append(
                    "\nNote: The following issues were found on lines that were not modified in the commit. " + "Because these issues can't be reported as line comments, they are summarized here:\n");
        } else if (extraIssuesTruncated) {
            builder.append("\n#### Top ").append(gitLabPluginConfiguration.maxGlobalIssues()).append(" issues\n");
        }

        builder.append("\n");

        appendSeverities(builder);
    }

    private void appendSeverities(StringBuilder builder) {
        int notReportedDisplayedIssueCount = 0;
        int reportedIssueCount = 0;

        for (Severity severity : Reporter.SEVERITIES) {
            List<Reporter.ReportIssue> reportIssues = reporter.getNotReportedOnDiffReportIssueForSeverity(severity);
            if (reportIssues != null && !reportIssues.isEmpty()) {
                for (Reporter.ReportIssue reportIssue : reportIssues) {
                    notReportedDisplayedIssueCount += appendIssue(builder, reportIssue, reportedIssueCount);
                    reportedIssueCount++;
                }
            }
        }

        appendMore(builder, notReportedDisplayedIssueCount);
    }

    private int appendIssue(StringBuilder builder, Reporter.ReportIssue reportIssue, int reportedIssueCount) {
        if (reportedIssueCount < gitLabPluginConfiguration.maxGlobalIssues()) {
            PostJobIssue postJobIssue = reportIssue.getPostJobIssue();
            builder.append("1. ") // Markdown increments automatically.
                    .append(markDownUtils.globalIssue(postJobIssue.severity(), postJobIssue.message(), postJobIssue.ruleKey().toString(), reportIssue.getUrl(), postJobIssue.componentKey()))
                    .append("\n");
            return 0;
        } else {
            return 1;
        }
    }

    private void appendMore(StringBuilder builder, int notReportedDisplayedIssueCount) {
        if (notReportedDisplayedIssueCount > 0) {
            builder.append("* ... ").append(notReportedDisplayedIssueCount).append(" more\n");
        }
    }
}
