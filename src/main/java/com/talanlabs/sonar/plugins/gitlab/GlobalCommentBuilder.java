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

import com.talanlabs.sonar.plugins.gitlab.freemarker.QualityGateConditionCountTemplateMethodModelEx;
import com.talanlabs.sonar.plugins.gitlab.freemarker.QualityGateConditionsTemplateMethodModelEx;
import com.talanlabs.sonar.plugins.gitlab.models.Issue;
import com.talanlabs.sonar.plugins.gitlab.models.QualityGate;
import com.talanlabs.sonar.plugins.gitlab.models.ReportIssue;
import org.sonar.api.batch.rule.Severity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GlobalCommentBuilder extends AbstractCommentBuilder {

    private final String author;
    private final QualityGate qualityGate;
    private final Reporter reporter;

    public GlobalCommentBuilder(GitLabPluginConfiguration gitLabPluginConfiguration, String author, QualityGate qualityGate, Reporter reporter, MarkDownUtils markDownUtils) {
        super(gitLabPluginConfiguration, gitLabPluginConfiguration.commitSHA().get(0), reporter.getReportIssues(), markDownUtils, "global", gitLabPluginConfiguration.globalTemplate());

        this.author = author;
        this.qualityGate = qualityGate;
        this.reporter = reporter;
    }

    @Override
    protected Map<String, Object> createContext() {
        Map<String, Object> root = super.createContext();
        root.put("author", author);
        Arrays.stream(QualityGate.Status.values()).forEach(status -> root.put(status.name(), status));
        root.put("qualityGate", createQualityGateContext());
        return root;
    }

    private Map<String, Object> createQualityGateContext() {
        if (qualityGate == null) {
            return null;
        }
        Map<String, Object> context = new HashMap<>();
        context.put("status", qualityGate.getStatus());
        context.put("conditions", new QualityGateConditionsTemplateMethodModelEx(qualityGate.getConditions() != null ? qualityGate.getConditions() : Collections.emptyList()));
        context.put("conditionCount", new QualityGateConditionCountTemplateMethodModelEx(qualityGate.getConditions() != null ? qualityGate.getConditions() : Collections.emptyList()));
        return context;
    }

    @Override
    protected String buildDefaultComment() {
        StringBuilder sb = new StringBuilder();

        appendQualityGate(sb);

        appendIssues(sb);

        return sb.toString();
    }

    private void appendQualityGate(StringBuilder sb) {
        if (qualityGate != null) {
            sb.append("SonarQube analysis indicates that quality gate is ").append(toStatusText(qualityGate.getStatus())).append(".\n");

            if (qualityGate.getConditions() != null) {
                qualityGate.getConditions().forEach(c -> appendCondition(sb, c));
            }

            sb.append("\n");
        }
    }

    private void appendCondition(StringBuilder sb, QualityGate.Condition condition) {
        sb.append("* ").append(condition.getMetricName()).append(" is ").append(toStatusText(condition.getStatus())).append(": Actual value ").append(condition.getActual());
        if (QualityGate.Status.WARN.equals(condition.getStatus())) {
            sb.append(" ").append(condition.getSymbol()).append(" ").append(condition.getWarning());
        } else if (QualityGate.Status.ERROR.equals(condition.getStatus())) {
            sb.append(" ").append(condition.getSymbol()).append(" ").append(condition.getError());
        }
        sb.append("\n");
    }

    private String toStatusText(QualityGate.Status status) {
        switch (status) {
            case OK:
                return "passed";
            case WARN:
                return "warning";
            case ERROR:
                return "failed";
            case NONE:
                return "none";
        }
        return "unknown";
    }

    private void appendIssues(StringBuilder sb) {
        int newIssues = reporter.getIssueCount();
        if (newIssues == 0) {
            sb.append("SonarQube analysis reported no issues.\n");
        } else {
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
        }
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
            builder.append("\n#### Top ").append(gitLabPluginConfiguration.maxGlobalIssues()).append(" issue").append(gitLabPluginConfiguration.maxGlobalIssues() > 1 ? "s" : "").append("\n");
        }

        builder.append("\n");

        appendSeverities(builder);
    }

    private void appendSeverities(StringBuilder builder) {
        int notReportedDisplayedIssueCount = 0;
        int reportedIssueCount = 0;

        for (Severity severity : Reporter.SEVERITIES) {
            List<ReportIssue> reportIssues = reporter.getNotReportedOnDiffReportIssueForSeverity(severity);
            if (reportIssues != null && !reportIssues.isEmpty()) {
                for (ReportIssue reportIssue : reportIssues) {
                    notReportedDisplayedIssueCount += appendIssue(builder, reportIssue, reportedIssueCount);
                    reportedIssueCount++;
                }
            }
        }

        appendMore(builder, notReportedDisplayedIssueCount);
    }

    private int appendIssue(StringBuilder builder, ReportIssue reportIssue, int reportedIssueCount) {
        Issue issue = reportIssue.getIssue();
        if (reportedIssueCount < gitLabPluginConfiguration.maxGlobalIssues()) {
            builder.append("1. ").append(markDownUtils.printIssue(issue.getSeverity(), issue.getMessage(), reportIssue.getRuleLink(), reportIssue.getUrl(), issue.getComponentKey()))
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
