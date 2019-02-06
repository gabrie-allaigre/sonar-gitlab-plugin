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

import com.talanlabs.gitlab.api.Paged;
import com.talanlabs.gitlab.api.v3.GitLabAPI;
import com.talanlabs.gitlab.api.v3.models.commits.GitLabCommit;
import com.talanlabs.gitlab.api.v3.models.commits.GitLabCommitComments;
import com.talanlabs.gitlab.api.v3.models.commits.GitLabCommitDiff;
import com.talanlabs.gitlab.api.v3.models.projects.GitLabProject;
import com.talanlabs.gitlab.api.v3.models.users.GitLabUser;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class GitLabApiV3Wrapper implements IGitLabApiWrapper {

    private static final Logger LOG = Loggers.get(GitLabApiV3Wrapper.class);

    private static final String COMMIT_CONTEXT = "sonarqube";

    private final GitLabPluginConfiguration config;
    private GitLabAPI gitLabAPIV3;
    private GitLabProject gitLabProject;
    private Map<String, List<GitLabCommitComments>> commitCommentPerRevision;
    private Map<String, Map<String, Set<Line>>> patchPositionByFile;

    public GitLabApiV3Wrapper(GitLabPluginConfiguration config) {
        this.config = config;
    }

    @Override
    public void init() {
        gitLabAPIV3 = GitLabAPI.connect(config.url(), config.userToken()).setIgnoreCertificateErrors(config.ignoreCertificate());
        if (config.isProxyConnectionEnabled()) {
            gitLabAPIV3.setProxy(config.getHttpProxy());
        }
        try {
            gitLabProject = getGitLabProject();

            commitCommentPerRevision = getCommitCommentsPerRevision(config.commitSHA());
            patchPositionByFile = getPatchPositionsToLineMapping(config.commitSHA());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to perform GitLab WS operation", e);
        }
    }

    void setGitLabAPI(GitLabAPI gitLabAPI) {
        this.gitLabAPIV3 = gitLabAPI;
    }

    private GitLabProject getGitLabProject() throws IOException {
        if (config.projectId() == null) {
            throw new IllegalStateException("Unable to find project ID null. Set the property sonar.gitlab.project_id");
        }

        try {
            GitLabProject project = gitLabAPIV3.getGitLabAPIProjects().getProject(config.projectId());
            if (project != null) {
                return project;
            }
        } catch (IOException e) {
            LOG.trace("Not found project with id", e);
        }

        Paged<GitLabProject> paged = gitLabAPIV3.getGitLabAPIProjects().getProjects(null, null, null, null, null, null);
        if (paged == null) {
            throw new IllegalStateException("Unable to find project ID " + config.projectId() + ". Either the project ID is incorrect or you don't have access to this project. Verify the configurations sonar.gitlab.project_id or sonar.gitlab.user_token");
        }
        List<GitLabProject> projects = new ArrayList<>();
        do {
            if (paged.getResults() != null) {
                projects.addAll(paged.getResults().stream().filter(this::isMatchingProject).collect(Collectors.toList()));
            }
        } while ((paged = paged.nextPage()) != null);

        if (projects.isEmpty()) {
            throw new IllegalStateException("Unable to find project ID " + config.projectId() + ". Either the project ID is incorrect or you don't have access to this project. Verify the configurations sonar.gitlab.project_id or sonar.gitlab.user_token");
        }
        if (projects.size() > 1) {
            throw new IllegalStateException("Multiple found projects for " + config.projectId());
        }
        return projects.get(0);
    }

    void setGitLabProject(GitLabProject gitLabProject) {
        this.gitLabProject = gitLabProject;
    }

    Map<String, List<GitLabCommitComments>> getCommitCommentsPerRevision(List<String> revisions) throws IOException {
        Map<String, List<GitLabCommitComments>> result = new HashMap<>();
        for (String revision : revisions) {
            Paged<GitLabCommitComments> paged = gitLabAPIV3.getGitLabAPICommits().getCommitComments(gitLabProject.getId(), revision, null);

            List<GitLabCommitComments> gitLabCommitCommentss = new ArrayList<>();
            do {
                if (paged.getResults() != null) {
                    gitLabCommitCommentss.addAll(paged.getResults());
                }
            } while ((paged = paged.nextPage()) != null);

            result.put(revision, gitLabCommitCommentss);
        }
        return result;
    }

    @Override
    public boolean hasSameCommitCommentsForFile(String revision, String path, Integer lineNumber, String body) {
        return getCommitCommentsForFile(revision, path)
                .stream()
                .anyMatch(c -> Objects.equals(c.getLine(), lineNumber) && c.getNote().equals(body));
    }

    Set<GitLabCommitComments> getCommitCommentsForFile(String revision, String path) {
        List<GitLabCommitComments> value = commitCommentPerRevision.get(revision);
        return Optional.ofNullable(value)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(c -> path.equals(c.getPath()))
                .collect(Collectors.toSet());
    }

    private Map<String, Map<String, Set<Line>>> getPatchPositionsToLineMapping(List<String> revisions) throws IOException {
        Map<String, Map<String, Set<Line>>> result = new HashMap<>();

        for (String revision : revisions) {
            Paged<GitLabCommitDiff> paged = gitLabAPIV3.getGitLabAPICommits().getCommitDiffs(gitLabProject.getId(), revision, null);
            List<GitLabCommitDiff> commitDiffs = new ArrayList<>();
            do {
                if (paged.getResults() != null) {
                    commitDiffs.addAll(paged.getResults());
                }
            } while ((paged = paged.nextPage()) != null);

            result.put(revision, commitDiffs
                    .stream()
                    .collect(Collectors.toMap(GitLabCommitDiff::getNewPath, d -> PatchUtils.getPositionsFromPatch(d.getDiff()))));
        }

        LOG.debug("getPatchPositionsToLineMapping {}", result);

        return result;
    }

    /**
     * Author Email is access only for admin gitlab user but search work for all users
     */
    @Override
    public String getUsernameForRevision(String revision) {
        try {
            GitLabCommit commit = gitLabAPIV3.getGitLabAPICommits().getCommit(gitLabProject.getId(), revision);

            Paged<GitLabUser> paged = gitLabAPIV3.getGitLabAPIUsers().getUsers(commit.getAuthorEmail(), null);
            List<GitLabUser> users = new ArrayList<>();
            do {
                if (paged.getResults() != null) {
                    users.addAll(paged.getResults());
                }
            } while ((paged = paged.nextPage()) != null);

            if (users.size() == 1) {
                return users.get(0).getUsername();
            }
            return users.stream().filter(x -> commit.getAuthorEmail().equals(x.getEmail()))
                    .map(GitLabUser::getUsername).findFirst().orElse(null);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create retrive author for commit " + revision, e);
        }
    }

    private boolean isMatchingProject(GitLabProject project) {
        return config.projectId().equals(project.getId().toString()) || verifyProjectName(project) || verifyProjectUrl(project);
    }

    private boolean verifyProjectUrl(GitLabProject project) {
        return config.projectId().equals(project.getHttpUrl()) || config.projectId().equals(project.getSshUrl()) || config.projectId().equals(project.getWebUrl());
    }

    private boolean verifyProjectName(GitLabProject project) {
        return config.projectId().equals(project.getPathWithNamespace()) || config.projectId().equals(project.getNameWithNamespace());
    }

    @Override
    public void createOrUpdateSonarQubeStatus(String status, String statusDescription) {
        try {
            gitLabAPIV3.getGitLabAPICommits().postCommitStatus(gitLabProject.getId(), getFirstCommitSHA(), status, config.refName(), COMMIT_CONTEXT, null, statusDescription);
        } catch (IOException e) {
            // Workaround for https://gitlab.com/gitlab-org/gitlab-ce/issues/25807
            if (e.getMessage() != null && e.getMessage().contains("Cannot transition status")) {
                LOG.debug("Transition status is already {}", status);
            } else {
                LOG.error("Unable to update commit status", e);
            }
        }
    }

    @Override
    public boolean hasFile(String path) {
        LOG.debug("hasFile {}", path);
        for (String revision : config.commitSHA()) {
            if (patchPositionByFile.get(revision).containsKey(path)) {
                LOG.debug("hasFile found {}", revision);
                return true;
            }
        }
        LOG.debug("hasFile notfound");
        return false;
    }

    @Override
    public String getRevisionForLine(File file,String path, int lineNumber) {
        String value = null;
        try {
            List<String> ss = Files.readAllLines(file.toPath());
            int l = lineNumber > 0 ? lineNumber - 1 : 0;
            value = ss.size() >= lineNumber ? ss.get(l) : null;
        } catch (IOException e) {
            LOG.trace("Not read all line for file {}", file, e);
        }
        Line line = new Line(lineNumber, value);

        LOG.debug("getRevisionForLine {} {}", path, line);

        for (String revision : config.commitSHA()) {
            LOG.debug("getRevisionForLine " + patchPositionByFile.get(revision));
            if (patchPositionByFile.get(revision).entrySet().stream().anyMatch(v ->
                    v.getKey().equals(path) && v.getValue().contains(line))) {
                LOG.debug("getRevisionForLine found {}");
                return revision;
            }
        }
        LOG.debug("getRevisionForLine notfound");
        return null;
    }

    @Override
    @CheckForNull
    public String getGitLabUrl(@Nullable String revision, String path, @Nullable Integer issueLine) {
        return gitLabProject.getWebUrl() + "/blob/" + (revision != null ? revision : getFirstCommitSHA()) + "/" + path + (issueLine != null ? ("#L" + issueLine) : "");
    }

    @Override
    public void createOrUpdateReviewComment(String revision, String fullPath, Integer line, String body) {
        try {
            gitLabAPIV3.getGitLabAPICommits().postCommitComments(gitLabProject.getId(), revision != null ? revision : getFirstCommitSHA(), body, fullPath, line, "new");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create or update review comment in file " + fullPath + " at line " + line, e);
        }
    }

    @Override
    public void addGlobalComment(String comment) {
        try {
            gitLabAPIV3.getGitLabAPICommits().postCommitComments(gitLabProject.getId(), getFirstCommitSHA(), comment, null, null, null);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to comment the commit", e);
        }
    }

    private String getFirstCommitSHA() {
        return config.commitSHA() != null && !config.commitSHA().isEmpty() ? config.commitSHA().get(0) : null;
    }

}
