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

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.rule.RuleKey;

public class IssueComparatorTest {

    private IssueComparator issueComparator;

    @Before
    public void before() {
        issueComparator = new IssueComparator();
    }

    @Test
    public void testNull() {
        Assertions.assertThat(issueComparator.compare(null, null)).isEqualTo(0);
        Assertions.assertThat(issueComparator.compare(new MyPostJobIssue(), null)).isEqualTo(-1);
        Assertions.assertThat(issueComparator.compare(null, new MyPostJobIssue())).isEqualTo(1);
    }

    @Test
    public void testSeverity() {
        Assertions.assertThat(issueComparator.compare(new MyPostJobIssue(Severity.BLOCKER, "toto", 1), new MyPostJobIssue(Severity.BLOCKER, "toto", 1))).isEqualTo(0);
        Assertions.assertThat(issueComparator.compare(new MyPostJobIssue(Severity.MAJOR, "toto", 1), new MyPostJobIssue(Severity.BLOCKER, "toto", 1))).isEqualTo(1);
        Assertions.assertThat(issueComparator.compare(new MyPostJobIssue(Severity.MAJOR, "toto", 1), new MyPostJobIssue(Severity.MINOR, "toto", 1))).isEqualTo(-1);
    }

    @Test
    public void testComponentKey() {
        Assertions.assertThat(issueComparator.compare(new MyPostJobIssue(Severity.BLOCKER, "a", 1), new MyPostJobIssue(Severity.BLOCKER, "b", 1))).isEqualTo(-1);
        Assertions.assertThat(issueComparator.compare(new MyPostJobIssue(Severity.BLOCKER, "b", 1), new MyPostJobIssue(Severity.BLOCKER, "a", 1))).isEqualTo(1);
    }

    @Test
    public void testSimple() {
        Assertions.assertThat(issueComparator.compare(new MyPostJobIssue(Severity.BLOCKER, "a", null), new MyPostJobIssue(Severity.BLOCKER, "a", null))).isEqualTo(0);
        Assertions.assertThat(issueComparator.compare(new MyPostJobIssue(Severity.BLOCKER, "a", 1), new MyPostJobIssue(Severity.BLOCKER, "a", 2))).isEqualTo(-1);
        Assertions.assertThat(issueComparator.compare(new MyPostJobIssue(Severity.BLOCKER, "a", 1), new MyPostJobIssue(Severity.BLOCKER, "a", null))).isEqualTo(1);
        Assertions.assertThat(issueComparator.compare(new MyPostJobIssue(Severity.BLOCKER, "a", 10), new MyPostJobIssue(Severity.BLOCKER, "a", 1))).isEqualTo(1);
        Assertions.assertThat(issueComparator.compare(new MyPostJobIssue(Severity.BLOCKER, "a", null), new MyPostJobIssue(Severity.BLOCKER, "a", 1))).isEqualTo(-1);
    }

    private static class MyPostJobIssue implements PostJobIssue {

        private Severity severity;
        private String componentKey;
        private Integer line;

        public MyPostJobIssue() {
        }

        public MyPostJobIssue(Severity severity, String componentKey, Integer line) {
            this.severity = severity;
            this.componentKey = componentKey;
            this.line = line;
        }

        @Override
        public String key() {
            return null;
        }

        @Override
        public RuleKey ruleKey() {
            return null;
        }

        @Override
        public String componentKey() {
            return componentKey;
        }

        @Override
        public InputComponent inputComponent() {
            return null;
        }

        @Override
        public Integer line() {
            return line;
        }

        @Override
        public String message() {
            return null;
        }

        @Override
        public Severity severity() {
            return severity;
        }

        @Override
        public boolean isNew() {
            return false;
        }
    }

}
