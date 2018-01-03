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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InlineCommentBuilder extends AbstractCommentBuilder {

    private final Integer lineNumber;
    private final String author;

    public InlineCommentBuilder(GitLabPluginConfiguration gitLabPluginConfiguration, String revision, String author, Integer lineNumber, List<Reporter.ReportIssue> reportIssues, MarkDownUtils markDownUtils) {
        super(gitLabPluginConfiguration, revision, reportIssues, markDownUtils, "inline", gitLabPluginConfiguration.inlineTemplate());

        this.lineNumber = lineNumber;
        this.author = author;
    }

    @Override
    protected String buildDefaultComment() {
        String msg = reportIssues.stream().map(reportIssue -> markDownUtils
                .printIssue(reportIssue.getPostJobIssue().severity(), reportIssue.getPostJobIssue().message(), reportIssue.getRuleLink(), null, null))
                .collect(Collectors.joining("\n"));
        if (gitLabPluginConfiguration.pingUser() && author != null) {
            msg += " @" + author;
        }
        return msg;
    }

    @Override
    protected Map<String, Object> createContext() {
        Map<String, Object> root = super.createContext();
        root.put("lineNumber", lineNumber);
        root.put("author", author);
        return root;
    }
}
