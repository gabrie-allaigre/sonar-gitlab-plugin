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
package com.talanlabs.sonar.plugins.gitlab.models;

public class ReportIssue {

    private Issue issue;
    private Rule rule;
    private String revision;
    private String url;
    private String file;
    private String ruleLink;
    private boolean reportedOnDiff;

    private ReportIssue() {
        // Nothing
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public Issue getIssue() {
        return issue;
    }

    public String getRevision() {
        return revision;
    }

    public String getUrl() {
        return url;
    }

    public String getFile() {
        return file;
    }

    public String getRuleLink() {
        return ruleLink;
    }

    public boolean isReportedOnDiff() {
        return reportedOnDiff;
    }

    public Rule getRule() {
        return rule;
    }

    public static class Builder {

        private ReportIssue reportIssue;

        private Builder() {
            this.reportIssue = new ReportIssue();
        }

        public Builder issue(Issue issue) {
            this.reportIssue.issue = issue;
            return this;
        }

        public Builder rule(Rule rule) {
            this.reportIssue.rule = rule;
            return this;
        }

        public Builder revision(String revision) {
            this.reportIssue.revision = revision;
            return this;
        }

        public Builder url(String url) {
            this.reportIssue.url = url;
            return this;
        }

        public Builder file(String file) {
            this.reportIssue.file = file;
            return this;
        }

        public Builder ruleLink(String ruleLink) {
            this.reportIssue.ruleLink = ruleLink;
            return this;
        }

        public Builder reportedOnDiff(boolean reportedOnDiff) {
            this.reportIssue.reportedOnDiff = reportedOnDiff;
            return this;
        }

        public ReportIssue build() {
            return reportIssue;
        }
    }
}
