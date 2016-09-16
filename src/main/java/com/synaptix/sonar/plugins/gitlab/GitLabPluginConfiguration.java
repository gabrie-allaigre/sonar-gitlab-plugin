/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2016-2016 Talanlabs
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
package com.synaptix.sonar.plugins.gitlab;

import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.config.Settings;

import javax.annotation.CheckForNull;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
@BatchSide
public class GitLabPluginConfiguration {

    private Settings settings;

    public GitLabPluginConfiguration(Settings settings) {
        this.settings = settings;
    }

    @CheckForNull
    public String projectId() {
        return settings.getString(GitLabPlugin.GITLAB_PROJECT_ID);
    }

    @CheckForNull
    public String commitSHA() {
        return settings.getString(GitLabPlugin.GITLAB_COMMIT_SHA);
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

    @CheckForNull
    public int maxGlobalIssues() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_GLOBAL_ISSUES);
    }

    @CheckForNull
    public int maxBlockerIssuesGate() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_BLOCKER_ISSUES_GATE);
    }
    @CheckForNull
    public int maxCriticalIssuesGate() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_CRITICAL_ISSUES_GATE);
    }
        @CheckForNull
    public int maxMajorIssuesGate() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_MAJOR_ISSUES_GATE);
    }
    @CheckForNull
    public int maxMinorIssuesGate() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_MINOR_ISSUES_GATE);
    }
    @CheckForNull
    public int maxInfoIssuesGate() {
        return settings.getInt(GitLabPlugin.GITLAB_MAX_INFO_ISSUES_GATE);
    }

    @CheckForNull
    public boolean ignoreFileNotModified() {
        return settings.getBoolean(GitLabPlugin.GITLAB_IGNORE_FILE);
    }

    @CheckForNull
    public String globalTemplate() {
        return settings.getString(GitLabPlugin.GITLAB_GLOBAL_TEMPLATE);
    }

    @CheckForNull
    public String inlineTemplate() {
        return settings.getString(GitLabPlugin.GITLAB_INLINE_TEMPLATE);
    }

    @CheckForNull
    public boolean commentNoIssue() {
        return settings.getBoolean(GitLabPlugin.GITLAB_COMMENT_NO_ISSUE);
    }

}
