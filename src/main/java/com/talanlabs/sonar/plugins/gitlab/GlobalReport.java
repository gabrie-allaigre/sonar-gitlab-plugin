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
import org.sonar.api.internal.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GlobalReport {

    private static final List<Severity> SEVERITIES = ImmutableList.of(Severity.BLOCKER, Severity.CRITICAL, Severity.MAJOR, Severity.MINOR, Severity.INFO);

    private final GitLabPluginConfiguration gitLabPluginConfiguration;
    private final MarkDownUtils markDownUtils;
    private int[] newIssuesBySeverity = new int[SEVERITIES.size()];
    private Map<Severity, List<IssueUrl>> notReportedOnDiffMap = new EnumMap<>(Severity.class);
    private int notReportedIssueCount = 0;

    public GlobalReport(GitLabPluginConfiguration gitLabPluginConfiguration, MarkDownUtils markDownUtils) {
        super();

        this.gitLabPluginConfiguration = gitLabPluginConfiguration;
        this.markDownUtils = markDownUtils;
    }

    private void increment(Severity severity) {
        this.newIssuesBySeverity[SEVERITIES.indexOf(severity)]++;
    }

    public String formatForMarkdown() {
        StringBuilder sb = new StringBuilder();

        int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
        if (newIssues == 0) {
            return "SonarQube analysis reported no issues.";
        }

        boolean hasInlineIssues = newIssues > notReportedIssueCount;
        boolean extraIssuesTruncated = notReportedIssueCount > gitLabPluginConfiguration.maxGlobalIssues();
        sb.append("SonarQube analysis reported ").append(newIssues).append(" issue").append(newIssues > 1 ? "s" : "").append("\n");

        appendSummaryBySeverity(sb);

        if (gitLabPluginConfiguration.tryReportIssuesInline() && hasInlineIssues) {
            sb.append("\nWatch the comments in this conversation to review them.\n");
        }

        if (notReportedIssueCount > 0) {
            appendExtraIssues(sb, hasInlineIssues, extraIssuesTruncated);
        }

        return sb.toString();
    }

    private void appendSummaryBySeverity(StringBuilder sb) {
        appendNewIssues(sb, Severity.BLOCKER);
        appendNewIssues(sb, Severity.CRITICAL);
        appendNewIssues(sb, Severity.MAJOR);
        appendNewIssues(sb, Severity.MINOR);
        appendNewIssues(sb, Severity.INFO);
    }

    private void appendNewIssues(StringBuilder builder, Severity severity) {
        int issueCount = newIssues(severity);
        if (issueCount > 0) {
            builder.append("* ").append(markDownUtils.getEmojiForSeverity(severity)).append(" ").append(issueCount).append(" ").append(severity.name().toLowerCase(Locale.ENGLISH)).append("\n");
        }
    }

    private void appendExtraIssues(StringBuilder builder, boolean hasInlineIssues, boolean extraIssuesTruncated) {
        if (gitLabPluginConfiguration.tryReportIssuesInline()) {
            if (hasInlineIssues || extraIssuesTruncated) {
                int extraCount;
                builder.append("\n#### ");
                if (notReportedIssueCount <= gitLabPluginConfiguration.maxGlobalIssues()) {
                    extraCount = notReportedIssueCount;
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

        int notReportedDisplayedIssueCount = 0;
        int i = 0;
        for (Severity severity : SEVERITIES) {
            List<IssueUrl> issueUrls = notReportedOnDiffMap.get(severity);
            if (issueUrls != null && !issueUrls.isEmpty()) {
                for (IssueUrl issueUrl : issueUrls) {
                    PostJobIssue postJobIssue = issueUrl.postJobIssue;
                    String msg = "1. " + markDownUtils.globalIssue(postJobIssue.severity(), postJobIssue.message(), postJobIssue.ruleKey().toString(), issueUrl.gitLabUrl, postJobIssue.componentKey());
                    if (i < gitLabPluginConfiguration.maxGlobalIssues()) {
                        builder.append(msg).append("\n");
                    } else {
                        notReportedDisplayedIssueCount++;
                    }
                    i++;
                }
            }
        }

        if (notReportedDisplayedIssueCount > 0) {
            builder.append("* ... ").append(notReportedDisplayedIssueCount).append(" more\n");
        }
    }

    public String getStatusDescription() {
        StringBuilder sb = new StringBuilder();
        printNewIssuesInline(sb);
        return sb.toString();
    }

    public String getStatus() {
        return aboveGates() ? "failed" : "success";
    }

    private boolean aboveGates() {
        return aboveGateForSeverity(Severity.BLOCKER, gitLabPluginConfiguration.maxBlockerIssuesGate()) || aboveGateForSeverity(Severity.CRITICAL, gitLabPluginConfiguration.maxCriticalIssuesGate())
                || aboveGateForSeverity(Severity.MAJOR, gitLabPluginConfiguration.maxMajorIssuesGate()) || aboveGateForSeverity(Severity.MINOR, gitLabPluginConfiguration.maxMinorIssuesGate())
                || aboveGateForSeverity(Severity.INFO, gitLabPluginConfiguration.maxInfoIssuesGate());
    }

    private boolean aboveGateForSeverity(Severity severity, int max) {
        return max != -1 && newIssues(severity) > max;
    }

    private int newIssues(Severity s) {
        return newIssuesBySeverity[SEVERITIES.indexOf(s)];
    }

    private void printNewIssuesInline(StringBuilder sb) {
        sb.append("SonarQube reported ");
        int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
        if (newIssues > 0) {
            sb.append(newIssues).append(" issue").append(newIssues > 1 ? "s" : "").append(",");
            int newCriticalOrBlockerIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL);
            if (newCriticalOrBlockerIssues > 0) {
                printNewIssuesInline(sb, Severity.CRITICAL);
                printNewIssuesInline(sb, Severity.BLOCKER);
            } else {
                sb.append(" no criticals or blockers");
            }
        } else {
            sb.append("no issues");
        }
    }

    private void printNewIssuesInline(StringBuilder sb, Severity severity) {
        int issueCount = newIssues(severity);
        if (issueCount > 0) {
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.append(" with ");
            } else {
                sb.append(" and ");
            }
            sb.append(issueCount).append(" ").append(severity.name().toLowerCase());
        }
    }

    public void process(PostJobIssue postJobIssue, @Nullable String gitLabUrl, boolean reportedOnDiff) {
        increment(postJobIssue.severity());
        if (!reportedOnDiff) {
            notReportedIssueCount++;

            List<IssueUrl> notReportedOnDiffs = notReportedOnDiffMap.computeIfAbsent(postJobIssue.severity(), k -> new ArrayList<>());
            notReportedOnDiffs.add(new IssueUrl(postJobIssue, gitLabUrl));
        }
    }

    public boolean hasNewIssue() {
        return newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO) > 0;
    }

    public static class IssueUrl {

        public final PostJobIssue postJobIssue;
        public final String gitLabUrl;

        public IssueUrl(PostJobIssue postJobIssue, String gitLabUrl) {
            this.postJobIssue = postJobIssue;
            this.gitLabUrl = gitLabUrl;
        }
    }
}
