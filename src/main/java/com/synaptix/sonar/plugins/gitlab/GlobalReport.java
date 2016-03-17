/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2016-2016 Talanlabs
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
package com.synaptix.sonar.plugins.gitlab;

import org.sonar.api.issue.Issue;
import org.sonar.api.rule.Severity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalReport {

    private static final String[] SEVERITIES = { Severity.BLOCKER, Severity.CRITICAL, Severity.MAJOR, Severity.MINOR, Severity.INFO };

    private final GitLabPluginConfiguration gitLabPluginConfiguration;
    private final MarkDownUtils markDownUtils;
    private int[] newIssuesBySeverity = new int[Severity.ALL.size()];
    private Map<String, List<String>> notReportedOnDiffMap = new HashMap<>();
    private int notReportedIssueCount = 0;

    public GlobalReport(GitLabPluginConfiguration gitLabPluginConfiguration, MarkDownUtils markDownUtils) {
        super();

        this.gitLabPluginConfiguration = gitLabPluginConfiguration;
        this.markDownUtils = markDownUtils;
    }

    private void increment(String severity) {
        this.newIssuesBySeverity[Severity.ALL.indexOf(severity)]++;
    }

    public String formatForMarkdown() {
        StringBuilder sb = new StringBuilder();
        printNewIssuesMarkdown(sb);
        if (hasNewIssue()) {
            sb.append("\nWatch the comments in this conversation to review them.");
        }
        if (notReportedIssueCount > 0) {
            sb.append("\nNote: the following issues could not be reported as comments because they are located on lines that are not displayed in this commit:\n");

            int notReportedDisplayedIssueCount = 0;
            int i = 0;
            for (String severity : SEVERITIES) {
                List<String> ss = notReportedOnDiffMap.get(severity);
                if (ss != null && !ss.isEmpty()) {
                    for (String s : ss) {
                        if (i < gitLabPluginConfiguration.maxGlobalIssues()) {
                            sb.append(s).append("\n");
                        } else {
                            notReportedDisplayedIssueCount++;
                        }
                        i++;
                    }
                }
            }

            if (notReportedDisplayedIssueCount > 0) {
                sb.append("* ... ").append(notReportedDisplayedIssueCount).append(" more\n");
            }
        }
        return sb.toString();
    }

    public String getStatusDescription() {
        StringBuilder sb = new StringBuilder();
        printNewIssuesInline(sb);
        return sb.toString();
    }

    public String getStatus() {
        return (newIssues(Severity.BLOCKER) > 0 || newIssues(Severity.CRITICAL) > 0) ? "failed" : "success";
    }

    private int newIssues(String s) {
        return newIssuesBySeverity[Severity.ALL.indexOf(s)];
    }

    private void printNewIssuesMarkdown(StringBuilder sb) {
        sb.append("SonarQube analysis reported ");
        int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
        if (newIssues > 0) {
            sb.append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(":\n");
            for (String severity : SEVERITIES) {
                printNewIssuesForMarkdown(sb, severity);
            }
        } else {
            sb.append("no issues.");
        }
    }

    private void printNewIssuesInline(StringBuilder sb) {
        sb.append("SonarQube reported ");
        int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
        if (newIssues > 0) {
            sb.append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(",");
            int newCriticalOrBlockerIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL);
            if (newCriticalOrBlockerIssues > 0) {
                printNewIssuesInline(sb, Severity.CRITICAL);
                printNewIssuesInline(sb, Severity.BLOCKER);
            } else {
                sb.append(" no critical nor blocker");
            }
        } else {
            sb.append("no issues");
        }
    }

    private void printNewIssuesInline(StringBuilder sb, String severity) {
        int issueCount = newIssues(severity);
        if (issueCount > 0) {
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.append(" with ");
            } else {
                sb.append(" and ");
            }
            sb.append(issueCount).append(" ").append(severity.toLowerCase());
        }
    }

    private void printNewIssuesForMarkdown(StringBuilder sb, String severity) {
        int issueCount = newIssues(severity);
        if (issueCount > 0) {
            sb.append("* ").append(MarkDownUtils.getImageMarkdownForSeverity(severity)).append(" ").append(issueCount).append(" ").append(severity.toLowerCase()).append("\n");
        }
    }

    public void process(Issue issue, @Nullable String gitLabUrl, boolean reportedOnDiff) {
        increment(issue.severity());
        if (!reportedOnDiff) {
            notReportedIssueCount++;

            List<String> notReportedOnDiffs = notReportedOnDiffMap.get(issue.severity());
            if (notReportedOnDiffs == null) {
                notReportedOnDiffs = new ArrayList<>();
                notReportedOnDiffMap.put(issue.severity(), notReportedOnDiffs);
            }

            notReportedOnDiffs
                    .add(new StringBuilder().append("* ").append(markDownUtils.globalIssue(issue.severity(), issue.message(), issue.ruleKey().toString(), gitLabUrl, issue.componentKey())).toString());
        }
    }

    public boolean hasNewIssue() {
        return newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO) > 0;
    }
}
