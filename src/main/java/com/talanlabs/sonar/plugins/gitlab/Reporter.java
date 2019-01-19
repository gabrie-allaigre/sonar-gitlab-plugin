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

import com.talanlabs.sonar.plugins.gitlab.models.*;
import org.apache.commons.lang3.StringEscapeUtils;
import org.sonar.api.batch.rule.Severity;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Reporter {

    static final List<Severity> SEVERITIES = Arrays.asList(Severity.BLOCKER, Severity.CRITICAL, Severity.MAJOR, Severity.MINOR, Severity.INFO);

    private final GitLabPluginConfiguration gitLabPluginConfiguration;

    private int[] newIssuesBySeverity = new int[SEVERITIES.size()];
    private Map<Severity, List<ReportIssue>> reportIssuesMap = new EnumMap<>(Severity.class);
    private Map<Severity, List<ReportIssue>> notReportedOnDiffMap = new EnumMap<>(Severity.class);
    private Map<String, Map<File, Map<Integer, List<ReportIssue>>>> revisionFileLineMap = new HashMap<>();
    private int notReportedIssueCount = 0;
    private List<ReportIssue> jsonIssues = new ArrayList<>();
    private QualityGate qualityGate;

    public Reporter(GitLabPluginConfiguration gitLabPluginConfiguration) {
        super();

        this.gitLabPluginConfiguration = gitLabPluginConfiguration;
    }

    public void setQualityGate(QualityGate qualityGate) {
        this.qualityGate = qualityGate;
    }

    public void process(Issue issue, @Nullable Rule rule, @Nullable String revision, @Nullable String gitLabUrl, @Nullable String src, String ruleLink, boolean reportedOnDiff) {
        String r = revision != null ? revision : gitLabPluginConfiguration.commitSHA().get(0);
        ReportIssue reportIssue = ReportIssue.newBuilder().issue(issue).rule(rule).revision(r).url(gitLabUrl).file(src).ruleLink(ruleLink).reportedOnDiff(reportedOnDiff).build();
        List<ReportIssue> reportIssues = reportIssuesMap.computeIfAbsent(issue.getSeverity(), k -> new ArrayList<>());
        reportIssues.add(reportIssue);

        if (!gitLabPluginConfiguration.jsonMode().equals(JsonMode.NONE)) {
            jsonIssues.add(reportIssue);
        }

        increment(issue.getSeverity());
        if (!reportedOnDiff) {
            notReportedIssueCount++;

            List<ReportIssue> notReportedOnDiffs = notReportedOnDiffMap.computeIfAbsent(issue.getSeverity(), k -> new ArrayList<>());
            notReportedOnDiffs.add(reportIssue);
        } else {
            Map<File, Map<Integer, List<ReportIssue>>> fileLineMap = revisionFileLineMap.computeIfAbsent(r, k -> new HashMap<>());
            Map<Integer, List<ReportIssue>> issuesByLine = fileLineMap.computeIfAbsent(issue.getFile(), k -> new HashMap<>());
            issuesByLine.computeIfAbsent(issue.getLine(), k -> new ArrayList<>()).add(reportIssue);
        }
    }

    private void increment(Severity severity) {
        this.newIssuesBySeverity[SEVERITIES.indexOf(severity)]++;
    }

    public boolean hasIssue() {
        return getIssueCount() > 0;
    }

    public String getStatus() {
        return isAboveGates() ? MessageHelper.FAILED_GITLAB_STATUS : MessageHelper.SUCCESS_GITLAB_STATUS;
    }

    public boolean isAboveGates() {
        return aboveQualityGate() || aboveImportantGates() || aboveOtherGates();
    }

    private boolean aboveQualityGate() {
        return qualityGate != null && (QualityGate.Status.ERROR.equals(qualityGate.getStatus()) || (QualityGateFailMode.WARN.equals(gitLabPluginConfiguration.qualityGateFailMode())
                && QualityGate.Status.WARN.equals(qualityGate.getStatus())));
    }

    private boolean aboveImportantGates() {
        return aboveGateForSeverity(Severity.BLOCKER, gitLabPluginConfiguration.maxBlockerIssuesGate()) || aboveGateForSeverity(Severity.CRITICAL, gitLabPluginConfiguration.maxCriticalIssuesGate());
    }

    private boolean aboveOtherGates() {
        return aboveGateForSeverity(Severity.MAJOR, gitLabPluginConfiguration.maxMajorIssuesGate()) || aboveGateForSeverity(Severity.MINOR, gitLabPluginConfiguration.maxMinorIssuesGate())
                || aboveGateForSeverity(Severity.INFO, gitLabPluginConfiguration.maxInfoIssuesGate());
    }

    private boolean aboveGateForSeverity(Severity severity, int max) {
        return max != -1 && getIssueCountForSeverity(severity) > max;
    }

    public int getIssueCountForSeverity(Severity s) {
        return newIssuesBySeverity[SEVERITIES.indexOf(s)];
    }

    public int getNotReportedIssueCount() {
        return notReportedIssueCount;
    }

    public int getIssueCount() {
        return getIssueCountForSeverity(Severity.BLOCKER) + getIssueCountForSeverity(Severity.CRITICAL) + getIssueCountForSeverity(Severity.MAJOR) + getIssueCountForSeverity(Severity.MINOR)
                + getIssueCountForSeverity(Severity.INFO);
    }

    public List<ReportIssue> getReportIssues() {
        return Collections.unmodifiableList(SEVERITIES.stream().map(reportIssuesMap::get).filter(l -> l != null && !l.isEmpty()).flatMap(List::stream).collect(Collectors.toList()));
    }

    public List<ReportIssue> getNotReportedOnDiffReportIssueForSeverity(Severity severity) {
        return Collections.unmodifiableList(notReportedOnDiffMap.getOrDefault(severity, Collections.emptyList()));
    }

    public Map<String, Map<File, Map<Integer, List<ReportIssue>>>> getFileLineMap() {
        return Collections.unmodifiableMap(revisionFileLineMap);
    }

    public boolean hasFileLine() {
        return !revisionFileLineMap.isEmpty();
    }

    public String getStatusDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("SonarQube reported ");
        printQualityGate(sb);
        printNewIssuesInline(sb);
        return sb.toString();
    }

    private void printQualityGate(StringBuilder sb) {
        if (qualityGate == null) {
            return;
        }

        sb.append("QualityGate is ").append(qualityGate.getStatus().name().toLowerCase()).append(",");

        List<QualityGate.Condition> conditions = qualityGate.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            sb.append(" with no conditions,");
        } else {
            printCondition(sb, QualityGate.Status.ERROR, conditions);
            printCondition(sb, QualityGate.Status.WARN, conditions);
            printCondition(sb, QualityGate.Status.OK, conditions);
            printCondition(sb, QualityGate.Status.NONE, conditions);
            if (sb.charAt(sb.length() - 1) != ',') {
                sb.append(",");
            }
        }
    }

    private void printCondition(StringBuilder sb, QualityGate.Status status, List<QualityGate.Condition> conditions) {
        int count = (int) conditions.stream().filter(c -> status.equals(c.getStatus())).count();
        if (count > 0) {
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.append(" with ");
            } else {
                sb.append(" and ");
            }
            sb.append(count).append(" ").append(status.name().toLowerCase());
        }
    }

    private void printNewIssuesInline(StringBuilder sb) {
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.append(" ");
        }
        int newIssues = getIssueCount();
        if (newIssues > 0) {
            sb.append(newIssues).append(" issue").append(newIssues > 1 ? "s" : "").append(",");
            printNewIssuesInline(sb, Severity.BLOCKER, gitLabPluginConfiguration.maxBlockerIssuesGate());
            printNewIssuesInline(sb, Severity.CRITICAL, gitLabPluginConfiguration.maxCriticalIssuesGate());
            printNewIssuesInline(sb, Severity.MAJOR, gitLabPluginConfiguration.maxMajorIssuesGate());
            printNewIssuesInline(sb, Severity.MINOR, gitLabPluginConfiguration.maxMinorIssuesGate());
            printNewIssuesInline(sb, Severity.INFO, gitLabPluginConfiguration.maxInfoIssuesGate());
        } else {
            sb.append("no issues");
        }
    }

    private void printNewIssuesInline(StringBuilder sb, Severity severity, int max) {
        int issueCount = getIssueCountForSeverity(severity);
        if (issueCount > 0) {
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.append(" with ");
            } else {
                sb.append(" and ");
            }
            sb.append(issueCount).append(" ").append(severity.name().toLowerCase());
            if (max != -1 && issueCount > max) {
                sb.append(" (fail)");
            }
        }
    }

    public String buildJson() {
        Function<ReportIssue, String> f;
        if (gitLabPluginConfiguration.jsonMode().equals(JsonMode.CODECLIMATE)) {
            f = this::buildIssueCodeQualityJson;
        } else if (gitLabPluginConfiguration.jsonMode().equals(JsonMode.SAST)) {
            f = this::buildIssueSastJson;
        } else {
            f = r -> "";
        }
        return jsonIssues.stream().map(f).collect(Collectors.joining(",", "[", "]"));
    }

    private String buildIssueCodeQualityJson(ReportIssue reportIssue) {
        Issue issue = reportIssue.getIssue();

        StringJoiner sj = new StringJoiner(",", "{", "}");
        sj.add("\"fingerprint\":\"" + issue.getKey() + "\"");
        sj.add("\"description\":\"" + prepareMessageJson(issue.getMessage()) + "\"");
        sj.add("\"location\":" + buildLocationCodeQualityJson(reportIssue));
        return sj.toString();
    }

    private String buildLocationCodeQualityJson(ReportIssue reportIssue) {
        Issue issue = reportIssue.getIssue();

        StringJoiner sj = new StringJoiner(",", "{", "}");
        sj.add("\"path\":\"" + reportIssue.getFile() + "\"");

        int line = issue.getLine() != null ? issue.getLine() : 0;

        sj.add("\"lines\": { \"begin\":" + line + ",\"end\":" + line + "}");
        return sj.toString();
    }

    private String buildIssueSastJson(ReportIssue reportIssue) {
        Issue issue = reportIssue.getIssue();

        StringJoiner sj = new StringJoiner(",", "{", "}");
        sj.add("\"tool\":\"sonarqube\"");
        sj.add("\"fingerprint\":\"" + issue.getKey() + "\"");
        sj.add("\"message\":\"" + prepareMessageJson(issue.getMessage()) + "\"");
        sj.add("\"file\":\"" + reportIssue.getFile() + "\"");
        sj.add("\"line\":\"" + (issue.getLine() != null ? issue.getLine() : 0) + "\"");
        sj.add("\"priority\":\"" + issue.getSeverity().name() + "\"");
        sj.add("\"solution\":\"" + reportIssue.getRuleLink() + "\"");
        return sj.toString();
    }

    private String prepareMessageJson(String message) {
        return StringEscapeUtils.escapeJson(message);
    }
}
