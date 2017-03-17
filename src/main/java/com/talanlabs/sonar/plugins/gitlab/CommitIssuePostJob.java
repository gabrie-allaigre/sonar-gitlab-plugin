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

import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.postjob.PostJob;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.batch.postjob.PostJobDescriptor;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Compute comments to be added on the commit.
 */
public class CommitIssuePostJob implements PostJob {

    private static final Logger LOG = Loggers.get(CommitIssuePostJob.class);

    private static final Comparator<PostJobIssue> ISSUE_COMPARATOR = new IssueComparator();

    private final GitLabPluginConfiguration gitLabPluginConfiguration;
    private final CommitFacade commitFacade;
    private final MarkDownUtils markDownUtils;

    public CommitIssuePostJob(GitLabPluginConfiguration gitLabPluginConfiguration, CommitFacade commitFacade, MarkDownUtils markDownUtils) {
        this.gitLabPluginConfiguration = gitLabPluginConfiguration;
        this.commitFacade = commitFacade;
        this.markDownUtils = markDownUtils;
    }

    @Override
    public void describe(PostJobDescriptor descriptor) {
        descriptor.name("GitLab Commit Issue Publisher").requireProperty(GitLabPlugin.GITLAB_URL, GitLabPlugin.GITLAB_USER_TOKEN, GitLabPlugin.GITLAB_PROJECT_ID, GitLabPlugin.GITLAB_COMMIT_SHA);
    }

    @Override
    public void execute(PostJobContext context) {
        Reporter report = new Reporter(gitLabPluginConfiguration);

        try {
            processIssues(report, context.issues());

            if (gitLabPluginConfiguration.tryReportIssuesInline() && report.hasFileLine()) {
                updateReviewComments(report);
            }

            if (!gitLabPluginConfiguration.disableGlobalComment() && (report.hasIssue() || gitLabPluginConfiguration.commentNoIssue())) {
                commitFacade.addGlobalComment(new GlobalCommentBuilder(gitLabPluginConfiguration, report, markDownUtils).buildForMarkdown());
            }

            String status = report.getStatus();
            String statusDescription = report.getStatusDescription();

            String message = String.format("Report status=%s, desc=%s", report.getStatus(), report.getStatusDescription());

            StatusNotificationsMode i = gitLabPluginConfiguration.statusNotificationsMode();
            if (i == StatusNotificationsMode.COMMIT_STATUS) {
                LOG.info(message);

                commitFacade.createOrUpdateSonarQubeStatus(status, statusDescription);
            } else if (i == StatusNotificationsMode.EXIT_CODE) {
                if ("failed".equals(status)) {
                    throw MessageException.of(message);
                } else {
                    LOG.info(message);
                }
            }
        } catch (MessageException e) {
            throw e;
        } catch (Exception e) {
            String msg = "SonarQube failed to complete the review of this commit";
            LOG.error(msg, e);

            StatusNotificationsMode i = gitLabPluginConfiguration.statusNotificationsMode();
            if (i == StatusNotificationsMode.COMMIT_STATUS) {
                commitFacade.createOrUpdateSonarQubeStatus("failed", msg + ": " + e.getMessage());
            }
        }
    }

    @Override
    public String toString() {
        return "GitLab Commit Issue Publisher";
    }

    private void processIssues(Reporter report, Iterable<PostJobIssue> issues) {
        getStreamPostJobIssue(issues).sorted(ISSUE_COMPARATOR).forEach(i -> processIssue(report, i));
    }

    private Stream<PostJobIssue> getStreamPostJobIssue(Iterable<PostJobIssue> issues) {
        return StreamSupport.stream(issues.spliterator(), false).filter(PostJobIssue::isNew).filter(i -> {
            InputComponent inputComponent = i.inputComponent();
            return !gitLabPluginConfiguration.onlyIssueFromCommitFile() || inputComponent == null || !inputComponent.isFile() || commitFacade.hasFile((InputFile) inputComponent);
        });
    }

    private void processIssue(Reporter report, PostJobIssue issue) {
        boolean reportedInline = false;
        InputComponent inputComponent = issue.inputComponent();
        if (gitLabPluginConfiguration.tryReportIssuesInline() && inputComponent != null && inputComponent.isFile()) {
            reportedInline = issue.line() != null && commitFacade.hasFileLine((InputFile) inputComponent, issue.line());
        }
        report.process(issue, commitFacade.getGitLabUrl(inputComponent, issue.line()), reportedInline);
    }

    private void updateReviewComments(Reporter report) {
        for (Map.Entry<InputFile, Map<Integer, List<Reporter.ReportIssue>>> entry : report.getFileLineMap().entrySet()) {
            for (Map.Entry<Integer, List<Reporter.ReportIssue>> entryPerLine : entry.getValue().entrySet()) {
                String body = new InlineCommentBuilder(gitLabPluginConfiguration, entryPerLine.getValue(), markDownUtils).buildForMarkdown();
                if(body != null && !body.isEmpty()) {
                    commitFacade.createOrUpdateReviewComment(entry.getKey(), entryPerLine.getKey(), body);
                }
            }
        }
    }
}
