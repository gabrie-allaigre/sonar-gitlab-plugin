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

import com.talanlabs.sonar.plugins.gitlab.models.JsonMode;
import com.talanlabs.sonar.plugins.gitlab.models.QualityGateFailMode;
import com.talanlabs.sonar.plugins.gitlab.models.StatusNotificationsMode;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.System2;

import java.net.Proxy;

public class GitLabPluginConfigurationTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private Settings settings;
    private GitLabPluginConfiguration config;

    @Before
    public void before() {
        settings = new Settings(new PropertyDefinitions(GitLabPlugin.definitions()));
        settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");
        config = new GitLabPluginConfiguration(settings, new System2());
    }

    @Test
    public void testGlobal() {
        Assertions.assertThat(config.url()).isEqualTo("https://gitlab.com");
        settings.setProperty(GitLabPlugin.GITLAB_URL, "https://gitlab.talanlabs.com/api");
        Assertions.assertThat(config.url()).isEqualTo("https://gitlab.talanlabs.com/api");

        Assertions.assertThat(config.ignoreCertificate()).isFalse();
        settings.setProperty(GitLabPlugin.GITLAB_IGNORE_CERT, "true");
        Assertions.assertThat(config.ignoreCertificate()).isTrue();

        settings.setProperty(GitLabPlugin.GITLAB_USER_TOKEN, "123465");
        Assertions.assertThat(config.userToken()).isEqualTo("123465");

        Assertions.assertThat(config.baseUrl()).isEqualTo("http://myserver/");
    }

    @Test
    public void testProject() {
        Assertions.assertThat(config.isEnabled()).isFalse();
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "3");
        Assertions.assertThat(config.commitSHA()).containsExactly("3");
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "3,4,5");
        Assertions.assertThat(config.commitSHA()).containsExactly("3", "4", "5");
        Assertions.assertThat(config.isEnabled()).isTrue();

        Assertions.assertThat(config.commentNoIssue()).isFalse();
        settings.setProperty(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE, "true");
        Assertions.assertThat(config.commentNoIssue()).isTrue();

        Assertions.assertThat(config.tryReportIssuesInline()).isTrue();
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS, "true");
        Assertions.assertThat(config.tryReportIssuesInline()).isFalse();

        settings.setProperty(GitLabPlugin.GITLAB_PROJECT_ID, "123");
        Assertions.assertThat(config.projectId()).isEqualTo("123");

        settings.setProperty(GitLabPlugin.GITLAB_REF_NAME, "123");
        Assertions.assertThat(config.refName()).isEqualTo("123");

        Assertions.assertThat(config.onlyIssueFromCommitFile()).isFalse();
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE, "true");
        Assertions.assertThat(config.onlyIssueFromCommitFile()).isTrue();

        Assertions.assertThat(config.onlyIssueFromCommitLine()).isFalse();
        settings.setProperty(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_LINE, "true");
        Assertions.assertThat(config.onlyIssueFromCommitLine()).isTrue();

        Assertions.assertThat(config.buildInitState()).isEqualTo(BuildInitState.PENDING);
        settings.setProperty(GitLabPlugin.GITLAB_BUILD_INIT_STATE, BuildInitState.RUNNING.getMeaning());
        Assertions.assertThat(config.buildInitState()).isEqualTo(BuildInitState.RUNNING);
        settings.setProperty(GitLabPlugin.GITLAB_BUILD_INIT_STATE, "toto");
        Assertions.assertThat(config.buildInitState()).isEqualTo(BuildInitState.PENDING);

        Assertions.assertThat(config.disableGlobalComment()).isFalse();
        settings.setProperty(GitLabPlugin.GITLAB_DISABLE_GLOBAL_COMMENT, "true");
        Assertions.assertThat(config.disableGlobalComment()).isTrue();

        Assertions.assertThat(config.statusNotificationsMode()).isEqualTo(StatusNotificationsMode.COMMIT_STATUS);
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());
        Assertions.assertThat(config.statusNotificationsMode()).isEqualTo(StatusNotificationsMode.EXIT_CODE);
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, "toto");
        Assertions.assertThat(config.statusNotificationsMode()).isEqualTo(StatusNotificationsMode.COMMIT_STATUS);
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());
        Assertions.assertThat(config.statusNotificationsMode()).isEqualTo(StatusNotificationsMode.NOTHING);

        Assertions.assertThat(config.qualityGateFailMode()).isEqualTo(QualityGateFailMode.ERROR);
        settings.setProperty(GitLabPlugin.GITLAB_QUALITY_GATE_FAIL_MODE, QualityGateFailMode.WARN.getMeaning());
        Assertions.assertThat(config.qualityGateFailMode()).isEqualTo(QualityGateFailMode.WARN);

        Assertions.assertThat(config.globalTemplate()).isNull();
        settings.setProperty(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE, "# Test");
        Assertions.assertThat(config.globalTemplate()).isEqualTo("# Test");

        Assertions.assertThat(config.inlineTemplate()).isNull();
        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, "# Test");
        Assertions.assertThat(config.inlineTemplate()).isEqualTo("# Test");

        Assertions.assertThat(config.pingUser()).isFalse();
        settings.setProperty(GitLabPlugin.GITLAB_PING_USER, "true");
        Assertions.assertThat(config.pingUser()).isTrue();

        Assertions.assertThat(config.uniqueIssuePerInline()).isFalse();
        settings.setProperty(GitLabPlugin.GITLAB_UNIQUE_ISSUE_PER_INLINE, "true");
        Assertions.assertThat(config.uniqueIssuePerInline()).isTrue();

        Assertions.assertThat(config.allIssues()).isFalse();
        settings.setProperty(GitLabPlugin.GITLAB_ALL_ISSUES, "true");
        Assertions.assertThat(config.allIssues()).isTrue();

        Assertions.assertThat(config.jsonMode()).isEqualTo(JsonMode.NONE);
        settings.setProperty(GitLabPlugin.GITLAB_JSON_MODE, "CODECLIMATE");
        Assertions.assertThat(config.jsonMode()).isEqualTo(JsonMode.CODECLIMATE);
        settings.setProperty(GitLabPlugin.GITLAB_JSON_MODE, "SAST");
        Assertions.assertThat(config.jsonMode()).isEqualTo(JsonMode.SAST);
        settings.setProperty(GitLabPlugin.GITLAB_JSON_MODE, "TOTO");
        Assertions.assertThat(config.jsonMode()).isEqualTo(JsonMode.NONE);

        Assertions.assertThat(config.queryMaxRetry()).isEqualTo(50);
        settings.setProperty(GitLabPlugin.GITLAB_QUERY_MAX_RETRY, "10");
        Assertions.assertThat(config.queryMaxRetry()).isEqualTo(10);

        Assertions.assertThat(config.queryWait()).isEqualTo(1000L);
        settings.setProperty(GitLabPlugin.GITLAB_QUERY_WAIT, "2000");
        Assertions.assertThat(config.queryWait()).isEqualTo(2000L);

        Assertions.assertThat(config.issueFilter()).isEqualTo(Severity.INFO);
        settings.setProperty(GitLabPlugin.GITLAB_ISSUE_FILTER, Severity.MAJOR.name());
        Assertions.assertThat(config.issueFilter()).isEqualTo(Severity.MAJOR);
        settings.setProperty(GitLabPlugin.GITLAB_ISSUE_FILTER, "TOTO");
        Assertions.assertThat(config.issueFilter()).isEqualTo(Severity.INFO);

        Assertions.assertThat(config.loadRule()).isFalse();
        settings.setProperty(GitLabPlugin.GITLAB_LOAD_RULES, "true");
        Assertions.assertThat(config.loadRule()).isTrue();
    }

    @Test
    public void testIssues() {
        Assertions.assertThat(config.maxBlockerIssuesGate()).isEqualTo(0);
        settings.setProperty(GitLabPlugin.GITLAB_MAX_BLOCKER_ISSUES_GATE, "10");
        Assertions.assertThat(config.maxBlockerIssuesGate()).isEqualTo(10);

        Assertions.assertThat(config.maxCriticalIssuesGate()).isEqualTo(0);
        settings.setProperty(GitLabPlugin.GITLAB_MAX_CRITICAL_ISSUES_GATE, "10");
        Assertions.assertThat(config.maxCriticalIssuesGate()).isEqualTo(10);

        Assertions.assertThat(config.maxMajorIssuesGate()).isEqualTo(-1);
        settings.setProperty(GitLabPlugin.GITLAB_MAX_MAJOR_ISSUES_GATE, "10");
        Assertions.assertThat(config.maxMajorIssuesGate()).isEqualTo(10);

        Assertions.assertThat(config.maxMinorIssuesGate()).isEqualTo(-1);
        settings.setProperty(GitLabPlugin.GITLAB_MAX_MINOR_ISSUES_GATE, "10");
        Assertions.assertThat(config.maxMinorIssuesGate()).isEqualTo(10);

        Assertions.assertThat(config.maxInfoIssuesGate()).isEqualTo(-1);
        settings.setProperty(GitLabPlugin.GITLAB_MAX_INFO_ISSUES_GATE, "10");
        Assertions.assertThat(config.maxInfoIssuesGate()).isEqualTo(10);

        Assertions.assertThat(config.prefixDirectory()).isNull();
        settings.setProperty(GitLabPlugin.GITLAB_PREFIX_DIRECTORY, "/src");
        Assertions.assertThat(config.prefixDirectory()).isEqualTo("/src");

        Assertions.assertThat(config.apiVersion()).isEqualTo(GitLabPlugin.V4_API_VERSION);
        settings.setProperty(GitLabPlugin.GITLAB_API_VERSION, GitLabPlugin.V3_API_VERSION);
        Assertions.assertThat(config.apiVersion()).isEqualTo(GitLabPlugin.V3_API_VERSION);
    }

    @Test
    public void testProxyConfiguration() {
        System2 system2 = Mockito.mock(System2.class);
        config = new GitLabPluginConfiguration(settings, system2);
        Assertions.assertThat(config.isProxyConnectionEnabled()).isFalse();
        Mockito.when(system2.property("http.proxyHost")).thenReturn("foo");
        Assertions.assertThat(config.isProxyConnectionEnabled()).isTrue();
        Mockito.when(system2.property("https.proxyHost")).thenReturn("bar");
        Assertions.assertThat(config.getHttpProxy()).isEqualTo(Proxy.NO_PROXY);

        settings.setProperty(GitLabPlugin.GITLAB_URL, "wrong url");
        thrown.expect(IllegalArgumentException.class);
        config.getHttpProxy();
    }
}
