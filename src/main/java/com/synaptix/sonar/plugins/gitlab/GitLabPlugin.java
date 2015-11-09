/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2015 Synaptix-Labs
 * contact@synaptix-labs.com
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

import org.sonar.api.Properties;
import org.sonar.api.Property;
import org.sonar.api.PropertyType;
import org.sonar.api.SonarPlugin;

import java.util.Arrays;
import java.util.List;

@Properties({ @Property(
        key = GitLabPlugin.GITLAB_URL,
        name = "GitLab URL",
        description = "URL to access GitLab",
        defaultValue = "https://gitlab.com",
        global = true), @Property(
        key = GitLabPlugin.GITLAB_MAX_GLOBAL_ISSUES,
        name = "GitLab Max Global GitLab",
        description = "Max issues to show in global comment",
        defaultValue = "10",
        type = PropertyType.INTEGER,
        global = true), @Property(
        key = GitLabPlugin.GITLAB_USER_TOKEN,
        name = "GitLab User Token",
        description = "GitLab user token is reporter role",
        global = true,
        project = true), @Property(
        key = GitLabPlugin.GITLAB_PROJECT_ID,
        name = "GitLab Project id",
        description = "The unique id, path with namespace, name with namespace, web url, ssh url or http url of the current project that GitLab",
        global = false,
        project = true), @Property(key = GitLabPlugin.GITLAB_COMMIT_SHA,
        name = "GitLab Commit SHA",
        description = "The commit revision for which project is built",
        global = false,
        project = false,
        module = false), @Property(key = GitLabPlugin.GITLAB_REF_NAME,
        name = "GitLab Ref Name",
        description = "The commit revision for which project is built",
        global = false,
        project = false,
        module = false) })

public class GitLabPlugin extends SonarPlugin {

    public static final String GITLAB_URL = "sonar.gitlab.url";
    public static final String GITLAB_MAX_GLOBAL_ISSUES = "sonar.gitlab.max_global_issues";
    public static final String GITLAB_USER_TOKEN = "sonar.gitlab.user_token";
    public static final String GITLAB_PROJECT_ID = "sonar.gitlab.project_id";
    public static final String GITLAB_COMMIT_SHA = "sonar.gitlab.commit_sha";
    public static final String GITLAB_REF_NAME = "sonar.gitlab.ref_name";

    @Override
    public List getExtensions() {
        return Arrays.asList(CommitIssuePostJob.class, GitLabPluginConfiguration.class, CommitProjectBuilder.class, CommitFacade.class, InputFileCacheSensor.class, InputFileCache.class,
                MarkDownUtils.class);
    }

}
