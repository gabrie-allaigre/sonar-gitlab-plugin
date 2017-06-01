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

import org.sonar.api.CoreProperties;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import javax.annotation.CheckForNull;
import java.net.*;
import java.util.Arrays;
import java.util.List;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
@BatchSide
public class GitLabPluginConfiguration {

    public static final String HTTP_PROXY_HOSTNAME = "http.proxyHost";
    public static final String HTTPS_PROXY_HOSTNAME = "https.proxyHost";
    public static final String PROXY_SOCKS_HOSTNAME = "socksProxyHost";
    public static final String HTTP_PROXY_PORT = "http.proxyPort";
    public static final String HTTPS_PROXY_PORT = "https.proxyPort";
    public static final String HTTP_PROXY_USER = "http.proxyUser";
    public static final String HTTP_PROXY_PASS = "http.proxyPassword";
    private static final Logger LOG = Loggers.get(GitLabPluginConfiguration.class);
    private final Settings settings;
    private final System2 system2;

    public GitLabPluginConfiguration(Settings settings, System2 system2) {
        super();

        this.settings = settings;
        this.system2 = system2;

        LOG.info("GlobalWorkingDir {}", settings.getString(CoreProperties.GLOBAL_WORKING_DIRECTORY));
    }

    public String projectId() {
        return settings.getString(GitLabPlugin.GITLAB_PROJECT_ID);
    }

    public List<String> commitSHA() {
        return Arrays.asList(settings.getStringArray(GitLabPlugin.GITLAB_COMMIT_SHA));
    }

    @CheckForNull
    public String refName() {
        return settings.getString(GitLabPlugin.GITLAB_REF_NAME);
    }

    @CheckForNull
    public String userToken() {
        return settings.getString(GitLabPlugin.GITLAB_USER_TOKEN);
    }

    public boolean isEnabled() {
        return settings.hasKey(GitLabPlugin.GITLAB_COMMIT_SHA);
    }

    @CheckForNull
    public String url() {
        return settings.getString(GitLabPlugin.GITLAB_URL);
    }

    public boolean ignoreCertificate() {
        return settings.getBoolean(GitLabPlugin.GITLAB_IGNORE_CERT);
    }

    public int maxGlobalIssues() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_GLOBAL_ISSUES);
    }

    public int maxBlockerIssuesGate() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_BLOCKER_ISSUES_GATE);
    }

    public int maxCriticalIssuesGate() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_CRITICAL_ISSUES_GATE);
    }

    public int maxMajorIssuesGate() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_MAJOR_ISSUES_GATE);
    }

    public int maxMinorIssuesGate() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_MINOR_ISSUES_GATE);
    }

    public int maxInfoIssuesGate() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_INFO_ISSUES_GATE);
    }

    public boolean tryReportIssuesInline() {
        return !settings.getBoolean(GitLabPlugin.GITLAB_DISABLE_INLINE_COMMENTS);
    }

    public boolean onlyIssueFromCommitFile() {
        return settings.getBoolean(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE);
    }

    public boolean onlyIssueFromCommitLine() {
        return settings.getBoolean(GitLabPlugin.GITLAB_ONLY_ISSUE_FROM_COMMIT_LINE);
    }

    public BuildInitState buildInitState() {
        BuildInitState b = BuildInitState.of(settings.getString(GitLabPlugin.GITLAB_BUILD_INIT_STATE));
        return b != null ? b : BuildInitState.PENDING;
    }

    public boolean disableGlobalComment() {
        return settings.getBoolean(GitLabPlugin.GITLAB_DISABLE_GLOBAL_COMMENT);
    }

    public StatusNotificationsMode statusNotificationsMode() {
        StatusNotificationsMode s = StatusNotificationsMode.of(settings.getString(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE));
        return s != null ? s : StatusNotificationsMode.COMMIT_STATUS;
    }

    @CheckForNull
    public String globalTemplate() {
        return settings.getString(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE);
    }

    @CheckForNull
    public String inlineTemplate() {
        return settings.getString(GitLabPlugin.GITLAB_INLINE_TEMPLATE);
    }

    public boolean commentNoIssue() {
        return settings.getBoolean(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE);
    }

    public boolean pingUser() {
        return settings.getBoolean(GitLabPlugin.GITLAB_PING_USER);
    }

    public boolean uniqueIssuePerInline() {
        return settings.getBoolean(GitLabPlugin.GITLAB_UNIQUE_ISSUE_PER_INLINE);
    }

    public String prefixDirectory() {
        return settings.getString(GitLabPlugin.GITLAB_PREFIX_DIRECTORY);
    }

    public String apiVersion() {
        return settings.getString(GitLabPlugin.GITLAB_API_VERSION);
    }

    /**
     * Checks if a proxy was passed with command line parameters or configured in the system.
     * If only an HTTP proxy was configured then it's properties are copied to the HTTPS proxy (like SonarQube configuration)
     *
     * @return True iff a proxy was configured to be used in the plugin.
     */
    public boolean isProxyConnectionEnabled() {
        return system2.property(HTTP_PROXY_HOSTNAME) != null || system2.property(HTTPS_PROXY_HOSTNAME) != null || system2.property(PROXY_SOCKS_HOSTNAME) != null;
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
}
