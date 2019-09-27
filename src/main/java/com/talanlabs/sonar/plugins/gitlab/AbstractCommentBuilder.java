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

import com.talanlabs.sonar.plugins.gitlab.freemarker.EmojiSeverityTemplateMethodModelEx;
import com.talanlabs.sonar.plugins.gitlab.freemarker.ImageSeverityTemplateMethodModelEx;
import com.talanlabs.sonar.plugins.gitlab.freemarker.IssueCountTemplateMethodModelEx;
import com.talanlabs.sonar.plugins.gitlab.freemarker.IssuesTemplateMethodModelEx;
import com.talanlabs.sonar.plugins.gitlab.freemarker.PrintTemplateMethodModelEx;
import com.talanlabs.sonar.plugins.gitlab.freemarker.RuleLinkTemplateMethodModelEx;
import com.talanlabs.sonar.plugins.gitlab.models.ReportIssue;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.commons.lang3.StringEscapeUtils;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Common;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractCommentBuilder {

    private static final Logger LOG = Loggers.get(AbstractCommentBuilder.class);

    protected final GitLabPluginConfiguration gitLabPluginConfiguration;
    protected final String revision;
    protected final List<ReportIssue> reportIssues;
    protected final MarkDownUtils markDownUtils;
    private final String templateName;
    private final String template;

    AbstractCommentBuilder(GitLabPluginConfiguration gitLabPluginConfiguration, String revision, List<ReportIssue> reportIssues, MarkDownUtils markDownUtils,
                           String templateName, String template) {
        super();

        this.gitLabPluginConfiguration = gitLabPluginConfiguration;
        this.revision = revision;
        this.reportIssues = reportIssues;
        this.markDownUtils = markDownUtils;
        this.templateName = templateName;
        this.template = template;
    }

    public String buildForMarkdown() {
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

        try (StringWriter sw = new StringWriter()) {
            new Template(templateName, template, cfg).process(createContext(), sw);
            return StringEscapeUtils.unescapeHtml4(sw.toString());
        } catch (IOException | TemplateException e) {
            LOG.error("Failed to create template {}", templateName, e);
            throw MessageException.of("Failed to create template " + templateName);
        }
    }

    protected Map<String, Object> createContext() {
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
        root.put("sonarUrl", gitLabPluginConfiguration.baseUrl());
        root.put("publishMode", true);
        // Report
        root.put("revision", revision);
        Arrays.stream(Severity.values()).forEach(severity -> root.put(severity.name(), severity));
        root.put("issueCount", new IssueCountTemplateMethodModelEx(reportIssues));
        root.put("issues", new IssuesTemplateMethodModelEx(reportIssues));
        root.put("print", new PrintTemplateMethodModelEx(markDownUtils));
        root.put("emojiSeverity", new EmojiSeverityTemplateMethodModelEx(markDownUtils));
        root.put("imageSeverity", new ImageSeverityTemplateMethodModelEx(markDownUtils));
        root.put("ruleLink", new RuleLinkTemplateMethodModelEx(gitLabPluginConfiguration));
        Arrays.stream(Common.RuleType.values()).forEach(type -> root.put(type.name(), type.name()));
        return root;
    }

    protected abstract String buildDefaultComment();
}
