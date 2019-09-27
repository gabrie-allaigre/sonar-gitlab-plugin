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

import com.talanlabs.sonar.plugins.gitlab.models.Issue;
import com.talanlabs.sonar.plugins.gitlab.models.JsonMode;
import com.talanlabs.sonar.plugins.gitlab.models.QualityGate;
import com.talanlabs.sonar.plugins.gitlab.models.ReportIssue;
import com.talanlabs.sonar.plugins.gitlab.models.Rule;
import org.sonar.api.ExtensionPoint;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.scanner.ScannerSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@ScannerSide
@ExtensionPoint
public class ReporterBuilder {

    private static final Logger LOG = Loggers.get(ReporterBuilder.class);

    private static final Comparator<Issue> ISSUE_COMPARATOR = new IssueComparator();

    private final GitLabPluginConfiguration gitLabPluginConfiguration;
    private final SonarFacade sonarFacade;
    private final CommitFacade commitFacade;
    private final MarkDownUtils markDownUtils;

    public ReporterBuilder(GitLabPluginConfiguration gitLabPluginConfiguration, SonarFacade sonarFacade, CommitFacade commitFacade, MarkDownUtils markDownUtils) {
        this.gitLabPluginConfiguration = gitLabPluginConfiguration;
        this.sonarFacade = sonarFacade;
        this.commitFacade = commitFacade;
        this.markDownUtils = markDownUtils;
    }

    /**
     * Build a reporter for issues
     *
     * @param qualityGate Quality Gate only for publish mode
     * @param issues      issues
     * @return a reporter
     */
    public Reporter build(QualityGate qualityGate, List<Issue> issues) {
        Reporter report = new Reporter(gitLabPluginConfiguration);

        report.setQualityGate(qualityGate);

        processIssues(report, issues);

        if (gitLabPluginConfiguration.tryReportIssuesInline() && report.hasFileLine()) {
            updateReviewComments(report);
        }

        if (!gitLabPluginConfiguration.disableGlobalComment() && isGlobalComments(qualityGate, report)) {
            updateGlobalComments(qualityGate, report);
        }

        if (!gitLabPluginConfiguration.jsonMode().equals(JsonMode.NONE)) {
            String json = report.buildJson();
            commitFacade.writeJsonFile(json);
        }

        return report;
    }

    private boolean isGlobalComments(QualityGate qualityGate, Reporter report) {
        return gitLabPluginConfiguration.commentNoIssue() || report.hasIssue() || (qualityGate != null && !QualityGate.Status.OK.equals(qualityGate.getStatus()));
    }

    private void processIssues(Reporter report, List<Issue> issues) {
        getStreamIssue(issues).sorted(ISSUE_COMPARATOR).forEach(i -> processIssue(report, i));
    }

    private Stream<Issue> getStreamIssue(List<Issue> issues) {
        return issues.stream().filter(p -> gitLabPluginConfiguration.allIssues() || p.isNewIssue()).filter(i -> {
            if (gitLabPluginConfiguration.onlyIssueFromCommitLine()) {
                return onlyIssueFromCommitLine(i);
            }
            return !gitLabPluginConfiguration.onlyIssueFromCommitFile() || i.getFile() == null || commitFacade.hasFile(i.getFile());
        });
    }

    private boolean onlyIssueFromCommitLine(Issue issue) {
        boolean hasFile = issue.getFile() != null && commitFacade.hasFile(issue.getFile());
        return hasFile && issue.getLine() != null && commitFacade.getRevisionForLine(issue.getFile(), issue.getLine()) != null;
    }

    private void processIssue(Reporter report, Issue issue) {
        boolean reportedInline = false;

        String revision = null;
        if (issue.getFile() != null && issue.getLine() != null) {
            revision = commitFacade.getRevisionForLine(issue.getFile(), issue.getLine());
            reportedInline = gitLabPluginConfiguration.tryReportIssuesInline() && revision != null;
        }
        LOG.debug("Revision for issue {} {} {}", issue, revision, reportedInline);
        LOG.debug("file {} {}", issue.getFile(), issue.getLine());

        String url = commitFacade.getGitLabUrl(revision, issue.getFile(), issue.getLine());
        String src = commitFacade.getSrc(issue.getFile());
        String ruleLink = commitFacade.getRuleLink(issue.getRuleKey());

        if (toSeverityNum(issue.getSeverity()) >= toSeverityNum(gitLabPluginConfiguration.issueFilter())) {
            Rule rule = null;
            if (gitLabPluginConfiguration.loadRule()) {
                rule = sonarFacade.getRule(issue.getRuleKey());
            }

            report.process(issue, rule, revision, url, src, ruleLink, reportedInline);
        }
    }

    private int toSeverityNum(Severity severity) {
        switch (severity) {
        case INFO:
            return 0;
        case MINOR:
            return 1;
        case MAJOR:
            return 2;
        case CRITICAL:
            return 3;
        case BLOCKER:
            return 4;
        }
        return 0;
    }

    private void updateReviewComments(Reporter report) {
        LOG.info("Will try to update review comments.");
        for (Map.Entry<String, Map<File, Map<Integer, List<ReportIssue>>>> entry : report.getFileLineMap().entrySet()) {
            String revision = entry.getKey();

            String username = commitFacade.getUsernameForRevision(revision);

            for (Map.Entry<File, Map<Integer, List<ReportIssue>>> entryPerFile : entry.getValue().entrySet()) {
                updateReviewComments(revision, username, entryPerFile.getKey(), entryPerFile.getValue());
            }
        }
    }

    private void updateReviewComments(String revision, String username, File file, Map<Integer, List<ReportIssue>> linePerIssuesMap) {
        for (Map.Entry<Integer, List<ReportIssue>> entryPerLine : linePerIssuesMap.entrySet()) {
            updateReviewComments(revision, username, file, entryPerLine.getKey(), entryPerLine.getValue());
        }
    }

    private void updateReviewComments(String revision, String username, File file, Integer lineNumber, List<ReportIssue> reportIssues) {
        LOG.debug("updateReviewComments {} {}", revision, reportIssues);
        if (gitLabPluginConfiguration.uniqueIssuePerInline()) {
            for (ReportIssue reportIssue : reportIssues) {
                updateReviewCommentsPerInline(revision, username, file, lineNumber, Collections.singletonList(reportIssue));
            }
        } else {
            updateReviewCommentsPerInline(revision, username, file, lineNumber, reportIssues);
        }
    }

    private void updateReviewCommentsPerInline(String revision, String username, File file, Integer lineNumber, List<ReportIssue> reportIssues) {
        String body = new InlineCommentBuilder(gitLabPluginConfiguration, revision, username, lineNumber, reportIssues, markDownUtils).buildForMarkdown();
        if (body != null && !body.trim().isEmpty()) {
            boolean exists = commitFacade.hasSameCommitCommentsForFile(revision, file, lineNumber, body);
            if (!exists) {
                commitFacade.createOrUpdateReviewComment(revision, file, lineNumber, body);
            }
        }
    }

    private void updateGlobalComments(QualityGate qualityGate, Reporter report) {
        String username = commitFacade.getUsernameForRevision(gitLabPluginConfiguration.commitSHA().get(0));
        String body = new GlobalCommentBuilder(gitLabPluginConfiguration, username, qualityGate, report, markDownUtils).buildForMarkdown();
        if (body != null && !body.trim().isEmpty()) {
            commitFacade.addGlobalComment(body);
        }
    }
}