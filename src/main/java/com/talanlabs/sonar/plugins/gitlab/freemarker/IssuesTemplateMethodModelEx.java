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
package com.talanlabs.sonar.plugins.gitlab.freemarker;

import com.talanlabs.sonar.plugins.gitlab.models.ReportIssue;
import com.talanlabs.sonar.plugins.gitlab.models.Rule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IssuesTemplateMethodModelEx extends AbstractIssuesTemplateMethodModelEx {

    public IssuesTemplateMethodModelEx(List<ReportIssue> reportIssues) {
        super(reportIssues);
    }

    @Override
    protected Object exec(Stream<ReportIssue> stream) {
        return stream.map(this::convertReportIssue).collect(Collectors.toList());
    }

    private Map<String, Object> convertReportIssue(ReportIssue reportIssue) {
        Map<String, Object> root = new HashMap<>();
        root.put("reportedOnDiff", reportIssue.isReportedOnDiff());
        root.put("url", reportIssue.getUrl());
        root.put("componentKey", reportIssue.getIssue().getComponentKey());
        root.put("severity", reportIssue.getIssue().getSeverity());
        root.put("line", reportIssue.getIssue().getLine());
        root.put("key", reportIssue.getIssue().getKey());
        root.put("message", reportIssue.getIssue().getMessage());
        root.put("ruleKey", reportIssue.getIssue().getRuleKey());
        root.put("new", reportIssue.getIssue().isNewIssue());
        root.put("ruleLink", reportIssue.getRuleLink());
        root.put("src", reportIssue.getFile());
        root.put("rule", reportIssue.getRule() != null ? convertRule(reportIssue.getRule()) : null);
        return root;
    }

    private Map<String, Object> convertRule(Rule rule) {
        Map<String, Object> root = new HashMap<>();
        root.put("key", rule.getKey());
        root.put("repo", rule.getRepo());
        root.put("name", rule.getName());
        root.put("description", rule.getDescription());
        root.put("type", rule.getType());
        root.put("debtRemFnType", rule.getDebtRemFnType());
        root.put("debtRemFnBaseEffort", rule.getDebtRemFnBaseEffort());
        return root;
    }
}
