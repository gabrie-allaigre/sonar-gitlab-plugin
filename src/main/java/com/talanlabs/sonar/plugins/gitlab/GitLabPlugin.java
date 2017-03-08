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

import org.sonar.api.Plugin;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;

import java.util.Arrays;
import java.util.List;

public class GitLabPlugin implements Plugin {

    public static final String GITLAB_URL = "sonar.gitlab.url";
    public static final String GITLAB_IGNORE_CERT = "sonar.gitlab.ignore_certificate";
    public static final String GITLAB_MAX_GLOBAL_ISSUES = "sonar.gitlab.max_global_issues";
    public static final String GITLAB_MAX_BLOCKER_ISSUES_GATE = "sonar.gitlab.max_blocker_issues_gate";
    public static final String GITLAB_MAX_CRITICAL_ISSUES_GATE = "sonar.gitlab.max_critical_issues_gate";
    public static final String GITLAB_MAX_MAJOR_ISSUES_GATE = "sonar.gitlab.max_major_issues_gate";
    public static final String GITLAB_MAX_MINOR_ISSUES_GATE = "sonar.gitlab.max_minor_issues_gate";
    public static final String GITLAB_MAX_INFO_ISSUES_GATE = "sonar.gitlab.max_info_issues_gate";
    public static final String GITLAB_USER_TOKEN = "sonar.gitlab.user_token";
    public static final String GITLAB_PROJECT_ID = "sonar.gitlab.project_id";
    public static final String GITLAB_COMMIT_SHA = "sonar.gitlab.commit_sha";
    public static final String GITLAB_REF_NAME = "sonar.gitlab.ref_name";
    public static final String GITLAB_GLOBAL_TEMPLATE = "sonar.gitlab.global_template";
    public static final String GITLAB_INLINE_TEMPLATE = "sonar.gitlab.inline_template";
    public static final String GITLAB_COMMENT_NO_ISSUE = "sonar.gitlab.comment_no_issue";
    public static final String GITLAB_DISABLE_INLINE_COMMENTS = "sonar.gitlab.disable_inline_comments";
    public static final String GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE = "sonar.gitlab.only_issue_from_commit_file";
    public static final String GITLAB_BUILD_INIT_STATE = "sonar.gitlab.build_init_state";
    public static final String GITLAB_DISABLE_GLOBAL_COMMENT = "sonar.gitlab.disable_global_comment";
    public static final String GITLAB_STATUS_NOTIFICATION_MODE = "sonar.gitlab.failure_notification_mode";

    public static final String CATEGORY = "gitlab";
    public static final String SUBCATEGORY = "reporting";

    public static List<PropertyDefinition> definitions() {
        return Arrays
                .asList(PropertyDefinition.builder(GITLAB_URL).name("GitLab url").description("URL to access GitLab.").category(CATEGORY).subCategory(SUBCATEGORY).defaultValue("https://gitlab.com")
                                .index(1).build(), PropertyDefinition.builder(GITLAB_IGNORE_CERT).name("GitLab Ignore Certificate").description("Ignore Certificate for access GitLab.").
                                category(CATEGORY).subCategory(SUBCATEGORY).
                                type(PropertyType.BOOLEAN).
                                defaultValue(String.valueOf(false)).
                                index(2).build(),
                        PropertyDefinition.builder(GITLAB_USER_TOKEN).name("GitLab User Token").description("GitLab user token is developer role.").category(CATEGORY).subCategory(SUBCATEGORY)
                                .type(PropertyType.PASSWORD).index(3).build(),
                        PropertyDefinition.builder(GITLAB_MAX_GLOBAL_ISSUES).name("GitLab Max Global Issues").description("Max issues to show in global comment.").category(CATEGORY)
                                .subCategory(SUBCATEGORY).type(PropertyType.INTEGER).defaultValue(String.valueOf(10)).index(4).build(),
                        PropertyDefinition.builder(GITLAB_PROJECT_ID).name("GitLab Project id")
                                .description("The unique id, path with namespace, name with namespace, web url, ssh url or http url of the current project that GitLab.").category(CATEGORY)
                                .subCategory(SUBCATEGORY).index(5).onlyOnQualifiers(Qualifiers.PROJECT).build(),
                        PropertyDefinition.builder(GITLAB_COMMIT_SHA).name("GitLab Commit SHA").description("The commit revision for which project is built.").category(CATEGORY)
                                .subCategory(SUBCATEGORY).index(6).hidden().build(),
                        PropertyDefinition.builder(GITLAB_REF_NAME).name("GitLab Ref Name").description("The commit revision for which project is built.").category(CATEGORY).subCategory(SUBCATEGORY)
                                .index(7).hidden().build(),
                        PropertyDefinition.builder(GITLAB_MAX_BLOCKER_ISSUES_GATE).name("Max Blocker Issues Gate").description("Max blocker issues to make the status fail.").category(CATEGORY)
                                .subCategory(SUBCATEGORY).type(PropertyType.INTEGER).defaultValue(String.valueOf(0)).onlyOnQualifiers(Qualifiers.PROJECT).index(9).build(),
                        PropertyDefinition.builder(GITLAB_MAX_CRITICAL_ISSUES_GATE).name("Max Critical Issues Gate").description("Max critical issues to make the status fail.").category(CATEGORY)
                                .subCategory(SUBCATEGORY).type(PropertyType.INTEGER).defaultValue(String.valueOf(0)).onlyOnQualifiers(Qualifiers.PROJECT).index(10).build(),
                        PropertyDefinition.builder(GITLAB_MAX_MAJOR_ISSUES_GATE).name("Max Major Issues Gate").description("Max major issues to make the status fail.").category(CATEGORY)
                                .subCategory(SUBCATEGORY).type(PropertyType.INTEGER).defaultValue(String.valueOf(-1)).onlyOnQualifiers(Qualifiers.PROJECT).index(11).build(),
                        PropertyDefinition.builder(GITLAB_MAX_MINOR_ISSUES_GATE).name("Max Minor Issues Gate").description("Max minor issues to make the status fail.").category(CATEGORY)
                                .subCategory(SUBCATEGORY).type(PropertyType.INTEGER).defaultValue(String.valueOf(-1)).onlyOnQualifiers(Qualifiers.PROJECT).index(12).build(),
                        PropertyDefinition.builder(GITLAB_MAX_INFO_ISSUES_GATE).name("Max Info Issues Gate").description("Max info issues to make the status fail.").category(CATEGORY)
                                .subCategory(SUBCATEGORY).type(PropertyType.INTEGER).defaultValue(String.valueOf(-1)).onlyOnQualifiers(Qualifiers.PROJECT).index(13).build(),
                        PropertyDefinition.builder(GITLAB_COMMENT_NO_ISSUE).name("Comment when no new issue").description("Add a comment even when there is no new issue.").category(CATEGORY)
                                .subCategory(SUBCATEGORY).type(PropertyType.BOOLEAN).defaultValue(String.valueOf(false)).index(14).build(),
                        PropertyDefinition.builder(GITLAB_DISABLE_INLINE_COMMENTS).name("Disable issue reporting as inline comments")
                                .description("Issues will not be reported as inline comments but only in the global summary comment.").category(CATEGORY).subCategory(SUBCATEGORY)
                                .type(PropertyType.BOOLEAN).defaultValue(String.valueOf(false)).index(15).build(),
                        PropertyDefinition.builder(GITLAB_ONLY_ISSUE_FROM_COMMIT_FILE).name("Show issue for commit file only").description("Issues will be reported if in current commit")
                                .category(CATEGORY).subCategory(SUBCATEGORY).type(PropertyType.BOOLEAN).defaultValue(String.valueOf(false)).index(16).hidden().build(),
                        PropertyDefinition.builder(GITLAB_BUILD_INIT_STATE).name("Build Initial State").description("State that should be the first when build commit status update is called.")
                                .category(CATEGORY).subCategory(SUBCATEGORY).type(PropertyType.SINGLE_SELECT_LIST)
                                .options(BuildInitState.PENDING.getMeaning(), BuildInitState.RUNNING.getMeaning()).defaultValue(BuildInitState.PENDING.getMeaning()).index(17).build(),
                        PropertyDefinition.builder(GITLAB_DISABLE_GLOBAL_COMMENT).name("Disable global comment").description("Disable global comment, report only inline.")
                                .category(CATEGORY).subCategory(SUBCATEGORY).defaultValue(String.valueOf(false)).index(18).build(),
                        PropertyDefinition.builder(GITLAB_STATUS_NOTIFICATION_MODE).name("Status notification mode").description("Status notification mode: commit-status or exit-code")
                                .category(CATEGORY).subCategory(SUBCATEGORY).type(PropertyType.SINGLE_SELECT_LIST)
                                .options(StatusNotificationsMode.COMMIT_STATUS.getMeaning(), StatusNotificationsMode.EXIT_CODE.getMeaning()).defaultValue(StatusNotificationsMode.COMMIT_STATUS.getMeaning())
                                .index(19).build(),
                        PropertyDefinition.builder(GITLAB_GLOBAL_TEMPLATE).name("Global template").description("Template for global comment in commit.").category(CATEGORY).subCategory(SUBCATEGORY)
                                .type(PropertyType.TEXT).index(20).build(),
                        PropertyDefinition.builder(GITLAB_INLINE_TEMPLATE).name("Inline template").description("Template for inline comment in commit.").category(CATEGORY).subCategory(SUBCATEGORY)
                                .type(PropertyType.TEXT).index(21).build());
    }

    @Override
    public void define(Context context) {
        context.addExtensions(CommitIssuePostJob.class, GitLabPluginConfiguration.class, CommitProjectBuilder.class, CommitFacade.class, MarkDownUtils.class).addExtensions(definitions());
    }
}
