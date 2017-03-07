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

import com.talanlabs.sonar.plugins.gitlab.freemarker.*;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

public class GlobalCommentBuilder {

    private static final Logger LOG = Loggers.get(GlobalCommentBuilder.class);

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
        String template = gitLabPluginConfiguration.globalTemplate();
        if (template != null && !template.isEmpty()) {
            return buildFreemarkerComment();
        }
        return buildDefaultComment();
    }

    private String buildFreemarkerComment() {
        Configuration cfg = new Configuration(Configuration.getVersion());
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);

        try {
            Template template = new Template("global", gitLabPluginConfiguration.globalTemplate(), cfg);

            StringWriter sw = new StringWriter();
            template.process(createContext(), sw);

            return sw.toString();
        } catch (IOException | TemplateException e) {
            LOG.error("Failed to create template global", e);
            throw MessageException.of("Failed to create template global");
        }
    }

    private Map<String, Object> createContext() {
        Map<String, Object> root = new HashMap<>();
        // Config
        root.put("projectId", gitLabPluginConfiguration.projectId());
        root.put("commitSHA", gitLabPluginConfiguration.commitSHA());
        root.put("refName", gitLabPluginConfiguration.refName());
        root.put("url", gitLabPluginConfiguration.url());
        root.put("maxGlobalIssues", gitLabPluginConfiguration.maxGlobalIssues());
        root.put("maxBlockerIssuesGate", gitLabPluginConfiguration.maxBlockerIssuesGate());
        root.put("maxCriticalIssuesGate", gitLabPluginConfiguration.maxCriticalIssuesGate());
        root.put("maxMajorIssuesGate", gitLabPluginConfiguration.maxMajorIssuesGate());
        root.put("maxMinorIssuesGate", gitLabPluginConfiguration.maxMinorIssuesGate());
        root.put("maxInfoIssuesGate", gitLabPluginConfiguration.maxInfoIssuesGate());
        root.put("disableIssuesInline", !gitLabPluginConfiguration.tryReportIssuesInline());
        root.put("disableGlobalComment", !gitLabPluginConfiguration.disableGlobalComment());
        root.put("onlyIssueFromCommitFile", gitLabPluginConfiguration.onlyIssueFromCommitFile());
        root.put("commentNoIssue", gitLabPluginConfiguration.commentNoIssue());
        // Report
        Arrays.stream(Severity.values()).forEach(severity -> root.put(severity.name(), severity));
        root.put("issueCount", new IssueCountTemplateMethodModelEx(reporter.getReportIssues()));
        root.put("issues", new IssuesTemplateMethodModelEx(reporter.getReportIssues()));
        root.put("print", new PrintTemplateMethodModelEx(markDownUtils));
        root.put("emojiSeverity", new EmojiSeverityTemplateMethodModelEx(markDownUtils));
        root.put("imageSeverity", new ImageSeverityTemplateMethodModelEx(markDownUtils));
        root.put("ruleLink", new RuleLinkTemplateMethodModelEx(markDownUtils));
        return root;
    }

    private String buildDefaultComment() {
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
            builder.append("\n#### Top ").append(gitLabPluginConfiguration.maxGlobalIssues()).append(" issue").append(gitLabPluginConfiguration.maxGlobalIssues() > 1 ? "s" : "").append("\n");
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
        PostJobIssue postJobIssue = reportIssue.getPostJobIssue();
        if (reportedIssueCount < gitLabPluginConfiguration.maxGlobalIssues()) {
            builder.append("1. ").append(markDownUtils.printIssue(postJobIssue.severity(), postJobIssue.message(), postJobIssue.ruleKey().toString(), reportIssue.getUrl(), postJobIssue.componentKey())).append("\n");
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
