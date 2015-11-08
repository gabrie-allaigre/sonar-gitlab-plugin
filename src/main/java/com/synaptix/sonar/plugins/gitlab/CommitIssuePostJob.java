/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2015 Synaptix-Labs
 * contact@synaptix-labs.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package com.synaptix.sonar.plugins.gitlab;

import org.sonar.api.batch.CheckProject;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.ProjectIssues;
import org.sonar.api.resources.Project;

import java.util.HashMap;
import java.util.Map;

/**
 * Compute comments to be added on the commit.
 */
public class CommitIssuePostJob implements org.sonar.api.batch.PostJob, CheckProject {

    private final GitLabPluginConfiguration gitLabPluginConfiguration;
    private final CommitFacade commitFacade;
    private final ProjectIssues projectIssues;
    private final InputFileCache inputFileCache;
    private final MarkDownUtils markDownUtils;

    public CommitIssuePostJob(GitLabPluginConfiguration gitLabPluginConfiguration,CommitFacade commitFacade, ProjectIssues projectIssues, InputFileCache inputFileCache,
            MarkDownUtils markDownUtils) {
        this.gitLabPluginConfiguration = gitLabPluginConfiguration;
        this.commitFacade = commitFacade;
        this.projectIssues = projectIssues;
        this.inputFileCache = inputFileCache;
        this.markDownUtils = markDownUtils;
    }

    @Override
    public boolean shouldExecuteOnProject(Project project) {
        return gitLabPluginConfiguration.isEnabled();
    }

    @Override
    public void executeOn(Project project, SensorContext context) {
        GlobalReport report = new GlobalReport(gitLabPluginConfiguration,markDownUtils);

        Map<InputFile, Map<Integer, StringBuilder>> commentsToBeAddedByLine = processIssues(report);

        updateReviewComments(commentsToBeAddedByLine);

        if (report.hasNewIssue()) {
            commitFacade.addGlobalComment(report.formatForMarkdown());
        }

        commitFacade.createOrUpdateSonarQubeStatus(report.getStatus(), report.getStatusDescription());
    }

    @Override
    public String toString() {
        return "GitLab Commit Issue Publisher";
    }

    private Map<InputFile, Map<Integer, StringBuilder>> processIssues(GlobalReport report) {
        Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine = new HashMap<>();
        for (Issue issue : projectIssues.issues()) {
            String severity = issue.severity();
            boolean isNew = issue.isNew();
            if (!isNew) {
                continue;
            }
            Integer issueLine = issue.line();
            InputFile inputFile = inputFileCache.byKey(issue.componentKey());
            if (inputFile != null && !commitFacade.hasFile(inputFile)) {
                // SONARGITUB-13 Ignore issues on files no modified by the P/R
                continue;
            }
            boolean reportedInline = false;
            if (inputFile != null && issueLine != null) {
                int line = issueLine.intValue();
                if (commitFacade.hasFileLine(inputFile, line)) {
                    String message = issue.message();
                    String ruleKey = issue.ruleKey().toString();
                    if (!commentToBeAddedByFileAndByLine.containsKey(inputFile)) {
                        commentToBeAddedByFileAndByLine.put(inputFile, new HashMap<Integer, StringBuilder>());
                    }
                    Map<Integer, StringBuilder> commentsByLine = commentToBeAddedByFileAndByLine.get(inputFile);
                    if (!commentsByLine.containsKey(line)) {
                        commentsByLine.put(line, new StringBuilder());
                    }
                    commentsByLine.get(line).append(markDownUtils.inlineIssue(severity, message, ruleKey)).append("\n");
                    reportedInline = true;
                }
            }
            report.process(issue, commitFacade.getGitLabUrl(inputFile, issueLine), reportedInline);

        }
        return commentToBeAddedByFileAndByLine;
    }

    private void updateReviewComments(Map<InputFile, Map<Integer, StringBuilder>> commentsToBeAddedByLine) {
        for (Map.Entry<InputFile, Map<Integer, StringBuilder>> entry : commentsToBeAddedByLine.entrySet()) {
            for (Map.Entry<Integer, StringBuilder> entryPerLine : entry.getValue().entrySet()) {
                String body = entryPerLine.getValue().toString();
                commitFacade.createOrUpdateReviewComment(entry.getKey(), entryPerLine.getKey(), body);
            }
        }
    }
}
