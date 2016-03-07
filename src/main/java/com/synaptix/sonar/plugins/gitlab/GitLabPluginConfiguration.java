/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2016 Talanlabs
 * gabriel.allaigre@synaptix-labs.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package com.synaptix.sonar.plugins.gitlab;

import org.sonar.api.BatchComponent;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.config.Settings;

import javax.annotation.CheckForNull;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class GitLabPluginConfiguration implements BatchComponent {

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

}
