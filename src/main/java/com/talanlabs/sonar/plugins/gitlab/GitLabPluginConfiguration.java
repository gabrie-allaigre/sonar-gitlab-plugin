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
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.ScannerSide;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import javax.annotation.CheckForNull;
import java.net.*;
import java.util.Arrays;
import java.util.List;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
@ScannerSide
public class GitLabPluginConfiguration {

    public static final String HTTP_PROXY_HOSTNAME = "http.proxyHost";
    public static final String HTTPS_PROXY_HOSTNAME = "https.proxyHost";
    public static final String PROXY_SOCKS_HOSTNAME = "socksProxyHost";
    public static final String HTTP_PROXY_PORT = "http.proxyPort";
    public static final String HTTPS_PROXY_PORT = "https.proxyPort";
    public static final String HTTP_PROXY_USER = "http.proxyUser";
    public static final String HTTP_PROXY_PASS = "http.proxyPassword";
    private static final Logger LOG = Loggers.get(GitLabPluginConfiguration.class);
    private final Configuration configuration;
    private final System2 system2;
    private final String baseUrl;

    public GitLabPluginConfiguration(Configuration configuration, System2 system2) {
        super();

        this.configuration = configuration;
        this.system2 = system2;

        String tempBaseUrl = configuration.hasKey(CoreProperties.SERVER_BASE_URL) ? configuration.get(CoreProperties.SERVER_BASE_URL).orElse(null) : configuration.get("sonar.host.url").orElse(null);
        if (tempBaseUrl == null) {
            tempBaseUrl = "http://localhost:9000";
        }
        if (!tempBaseUrl.endsWith("/")) {
            tempBaseUrl += "/";
        }
        this.baseUrl = tempBaseUrl;
    }

    public String projectId() {
        return configuration.get(GitLabPlugin.GITLAB_PROJECT_ID).orElse(null);
    }

    public List<String> commitSHA() {
        return Arrays.asList(configuration.getStringArray(GitLabPlugin.GITLAB_COMMIT_SHA));
    }

    @CheckForNull
    public String refName() {
        return configuration.get(GitLabPlugin.GITLAB_REF_NAME).orElse(null);
    }

    @CheckForNull
    public String userToken() {
        return configuration.get(GitLabPlugin.GITLAB_USER_TOKEN).orElse(null);
    }

    public boolean isEnabled() {
        return configuration.hasKey(GitLabPlugin.GITLAB_COMMIT_SHA);
    }

    @CheckForNull
    public String url() {
        return configuration.get(GitLabPlugin.GITLAB_URL).orElseGet(null);
    }

    public boolean ignoreCertificate() {
        return configuration.getBoolean(GitLabPlugin.GITLAB_IGNORE_CERT).orElse(false);
    }

    public int maxGlobalIssues() {
        return configuration.getInt(GitLabPlugin.GITLAB_MAX_GLOBAL_ISSUES).orElse(10);
    }

    public int maxBlockerIssuesGate() {
        return configuration.getInt(GitLabPlugin.GITLAB_MAX_BLOCKER_ISSUES_GATE).orElse(0);
    }

    public int maxCriticalIssuesGate() {
        return configuration.getInt(GitLabPlugin.GITLAB_MAX_CRITICAL_ISSUES_GATE).orElse(0);
    }

    public int maxMajorIssuesGate() {
        return configuration.getInt(GitLabPlugin.GITLAB_MAX_MAJOR_ISSUES_GATE).orElse(-1);
    }

    public int maxMinorIssuesGate() {
        return configuration.getInt(GitLabPlugin.GITLAB_MAX_MINOR_ISSUES_GATE).orElse(-1);
    }

    public int maxInfoIssuesGate() {
        return configuration.getInt(GitLabPlugin.GITLAB_MAX_INFO_ISSUES_GATE).orElse(-1);
    }

    public boolean tryReportIssuesInline() {
        return !configuration.getBoolean(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS).orElse(true);
    }

    public boolean onlyIssueFromCommitFile() {
        return configuration.getBoolean(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE).orElse(false);
    }

    public boolean onlyIssueFromCommitLine() {
        return configuration.getBoolean(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_LINE).orElse(false);
    }

    public BuildInitState buildInitState() {
        BuildInitState b = BuildInitState.of(configuration.get(GitLabPlugin.GITLAB_BUILD_INIT_STATE).orElse(null));
        return b != null ? b : BuildInitState.PENDING;
    }

    public boolean disableGlobalComment() {
        return configuration.getBoolean(GitLabPlugin.GITLAB_DISABLE_GLOBAL_COMMENT).orElse(false);
    }

    public StatusNotificationsMode statusNotificationsMode() {
        StatusNotificationsMode s = StatusNotificationsMode.of(configuration.get(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE).orElse(null));
        return s != null ? s : StatusNotificationsMode.COMMIT_STATUS;
    }

    public QualityGateFailMode qualityGateFailMode() {
        QualityGateFailMode s = QualityGateFailMode.of(configuration.get(GitLabPlugin.GITLAB_QUALITY_GATE_FAIL_MODE).orElse(null));
        return s != null ? s : QualityGateFailMode.ERROR;
    }

    @CheckForNull
    public String globalTemplate() {
        return configuration.get(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE).orElse(null);
    }

    @CheckForNull
    public String inlineTemplate() {
        return configuration.get(GitLabPlugin.GITLAB_INLINE_TEMPLATE).orElse(null);
    }

    public boolean commentNoIssue() {
        return configuration.getBoolean(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE).orElse(false);
    }

    public boolean pingUser() {
        return configuration.getBoolean(GitLabPlugin.GITLAB_PING_USER).orElse(false);
    }

    public boolean uniqueIssuePerInline() {
        return configuration.getBoolean(GitLabPlugin.GITLAB_UNIQUE_ISSUE_PER_INLINE).orElse(false);
    }

    public String prefixDirectory() {
        return configuration.get(GitLabPlugin.GITLAB_PREFIX_DIRECTORY).orElse(null);
    }

    public String apiVersion() {
        return configuration.get(GitLabPlugin.GITLAB_API_VERSION).orElse(GitLabPlugin.V4_API_VERSION);
    }

    public boolean allIssues() {
        return configuration.getBoolean(GitLabPlugin.GITLAB_ALL_ISSUES).orElse(false);
    }

    public JsonMode jsonMode() {
        JsonMode s = JsonMode.of(configuration.get(GitLabPlugin.GITLAB_JSON_MODE).orElse(null));
        return s != null ? s : JsonMode.NONE;
    }

    public int queryMaxRetry() {
        return configuration.getInt(GitLabPlugin.GITLAB_QUERY_MAX_RETRY).orElse(50);
    }

    public int queryWait() {
        return configuration.getInt(GitLabPlugin.GITLAB_QUERY_WAIT).orElse(1000);
    }

    public Severity issueFilter() {
        String name = configuration.get(GitLabPlugin.GITLAB_ISSUE_FILTER).orElse(null);
        if (name == null) {
            return Severity.INFO;
        }
        try {
            return Severity.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Severity.INFO;
        }
    }

    public boolean loadRule() {
        return configuration.getBoolean(GitLabPlugin.GITLAB_LOAD_RULES).orElse(false);
    }

    /**
     * Checks if a proxy was passed with command line parameters or configured in the system.
     * If only an HTTP proxy was configured then it's properties are copied to the HTTPS proxy (like SonarQube configuration)
     *
     * @return True iff a proxy was configured to be used in the plugin.
     */
    public boolean isProxyConnectionEnabled() {
        if (configuration.getBoolean(GitLabPlugin.GITLAB_DISABLE_PROXY).orElse(false)) {
            return false;
        }
        return (system2.property(HTTP_PROXY_HOSTNAME) != null || system2.property(HTTPS_PROXY_HOSTNAME) != null || system2.property(PROXY_SOCKS_HOSTNAME) != null);
    }

    public Proxy getHttpProxy() {
        try {
            if (system2.property(HTTP_PROXY_HOSTNAME) != null && system2.property(HTTPS_PROXY_HOSTNAME) == null) {
                System.setProperty(HTTPS_PROXY_HOSTNAME, system2.property(HTTP_PROXY_HOSTNAME));
                System.setProperty(HTTPS_PROXY_PORT, system2.property(HTTP_PROXY_PORT));
            }

            String proxyUser = system2.property(HTTP_PROXY_USER);
            String proxyPass = system2.property(HTTP_PROXY_PASS);

            if (proxyUser != null && proxyPass != null) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    public PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
                    }
                });
            }

            Proxy selectedProxy = ProxySelector.getDefault().select(new URI(url())).get(0);

            if (selectedProxy.type() == Proxy.Type.DIRECT) {
                LOG.debug("There was no suitable proxy found to connect to GitLab - direct connection is used ");
            }

            LOG.info("A proxy has been configured - {}", selectedProxy.toString());
            return selectedProxy;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Unable to perform GitLab WS operation - url in wrong format: " + url(), e);
        }
    }

    public String baseUrl() {
        return baseUrl;
    }

    public boolean isMergeRequestDiscussion() {
        return configuration.getBoolean(GitLabPlugin.GITLAB_MERGE_REQUEST_DISCUSSION).orElse(false);
    }
}
