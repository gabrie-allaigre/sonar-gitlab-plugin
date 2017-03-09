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
package com.talanlabs.sonar.plugins.gitlab.freemarker;

import com.talanlabs.sonar.plugins.gitlab.Reporter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IssuesTemplateMethodModelEx extends AbstractIssuesTemplateMethodModelEx {

    public IssuesTemplateMethodModelEx(List<Reporter.ReportIssue> reportIssues) {
        super(reportIssues);
    }

    @Override
    protected Object exec(Stream<Reporter.ReportIssue> stream) {
        return stream.map(this::convertReportIssue).collect(Collectors.toList());
    }

    private Map<String, Object> convertReportIssue(Reporter.ReportIssue reportIssue) {
        Map<String, Object> root = new HashMap<>();
        root.put("reportedOnDiff", reportIssue.isReportedOnDiff());
        root.put("url", reportIssue.getUrl());
        root.put("componentKey", reportIssue.getPostJobIssue().componentKey());
        root.put("severity", reportIssue.getPostJobIssue().severity());
        root.put("line", reportIssue.getPostJobIssue().line());
        root.put("key", reportIssue.getPostJobIssue().key());
        root.put("message", reportIssue.getPostJobIssue().message());
        root.put("ruleKey", reportIssue.getPostJobIssue().ruleKey().toString());
        root.put("new", reportIssue.getPostJobIssue().isNew());
        return root;
    }
}
