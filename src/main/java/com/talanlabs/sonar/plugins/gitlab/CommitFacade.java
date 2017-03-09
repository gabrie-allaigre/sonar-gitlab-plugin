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

import com.talanlabs.gitlab.api.GitLabAPI;
import com.talanlabs.gitlab.api.Paged;
import com.talanlabs.gitlab.api.models.commits.GitLabCommit;
import com.talanlabs.gitlab.api.models.commits.GitLabCommitComments;
import com.talanlabs.gitlab.api.models.commits.GitLabCommitDiff;
import com.talanlabs.gitlab.api.models.projects.GitLabProject;
import com.talanlabs.gitlab.api.models.users.GitLabUser;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Facade for all WS interaction with GitLab.
 */
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
@BatchSide
public class CommitFacade {

    private static final Logger LOG = Loggers.get(CommitFacade.class);

    // http://en.wikipedia.org/wiki/Diff_utility#Unified_format
    private static final Pattern PATCH_PATTERN = Pattern.compile("@@\\p{Space}-[0-9]+(?:,[0-9]+)?\\p{Space}\\+([0-9]+)(?:,[0-9]+)?\\p{Space}@@.*");
    private static final String COMMIT_CONTEXT = "sonarqube";

    private final GitLabPluginConfiguration config;
    private File gitBaseDir;
    private GitLabAPI gitLabAPI;
    private GitLabProject gitLabProject;
    private Map<String, List<GitLabCommitComments>> commitCommentPerRevision;
    private Map<String, Map<String, Set<Line>>> patchPositionByFile;

    public CommitFacade(GitLabPluginConfiguration config) {
        this.config = config;
    }

    public void init(File projectBaseDir) {
        initGitBaseDir(projectBaseDir);

        gitLabAPI = GitLabAPI.connect(config.url(), config.userToken()).setIgnoreCertificateErrors(config.ignoreCertificate());
        if (config.isProxyConnectionEnabled()) {
            gitLabAPI.setProxy(config.getHttpProxy());
        }
        try {
            gitLabProject = getGitLabProject();

            commitCommentPerRevision = getCommitCommentsPerRevision(config.commitSHA());
            patchPositionByFile = getPatchPositionsToLineMapping(config.commitSHA());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to perform GitLab WS operation", e);
        }
    }

    void initGitBaseDir(File projectBaseDir) {
        File detectedGitBaseDir = findGitBaseDir(projectBaseDir);
        if (detectedGitBaseDir == null) {
            LOG.debug("Unable to find Git root directory. Is " + projectBaseDir + " part of a Git repository?");
            setGitBaseDir(projectBaseDir);
        } else {
            setGitBaseDir(detectedGitBaseDir);
        }
    }

    void setGitLabAPI(GitLabAPI gitLabAPI) {
        this.gitLabAPI = gitLabAPI;
    }

    private File findGitBaseDir(@Nullable File baseDir) {
        if (baseDir == null) {
            return null;
        }
        if (new File(baseDir, ".git").exists()) {
            this.gitBaseDir = baseDir;
            return baseDir;
        }
        return findGitBaseDir(baseDir.getParentFile());
    }

    void setGitBaseDir(File gitBaseDir) {
        this.gitBaseDir = gitBaseDir;
    }

    private GitLabProject getGitLabProject() throws IOException {
        if (config.projectId() == null) {
            throw new IllegalStateException("Unable found project for null project name. Set Configuration sonar.gitlab.project_id");
        }

        try {
            GitLabProject project = gitLabAPI.getGitLabAPIProjects().getProject(config.projectId());
            if (project != null) {
                return project;
            }
        } catch (IOException e) {
            LOG.trace("Not found project with id", e);
        }

        Paged<GitLabProject> paged = gitLabAPI.getGitLabAPIProjects().getProjects(null, null, null, null, null, null);
        if (paged == null) {
            throw new IllegalStateException("Unable found project for " + config.projectId() + " Verify Configuration sonar.gitlab.project_id or sonar.gitlab.user_token access project");
        }
        List<GitLabProject> projects = new ArrayList<>();
        do {
            if (paged.getResults() != null) {
                projects.addAll(paged.getResults().stream().filter(this::isMatchingProject).collect(Collectors.toList()));
            }
        } while ((paged = paged.nextPage()) != null);

        if (projects.isEmpty()) {
            throw new IllegalStateException("Unable found project for " + config.projectId() + " Verify Configuration sonar.gitlab.project_id or sonar.gitlab.user_token access project");
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
            Paged<GitLabCommitComments> paged = gitLabAPI.getGitLabAPICommits().getCommitComments(gitLabProject.getId(), revision, null);

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


    Set<GitLabCommitComments> getCommitCommentsForFile(String revision, InputFile inputFile) {
        String path = getPath(inputFile);
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
            Paged<GitLabCommitDiff> paged = gitLabAPI.getGitLabAPICommits().getCommitDiffs(gitLabProject.getId(), revision, null);
            List<GitLabCommitDiff> commitDiffs = new ArrayList<>();
            do {
                if (paged.getResults() != null) {
                    commitDiffs.addAll(paged.getResults());
                }
            } while ((paged = paged.nextPage()) != null);

            result.put(revision, commitDiffs
                    .stream()
                    .collect(Collectors.toMap(GitLabCommitDiff::getNewPath, d -> getPositionsFromPatch(d.getDiff()))));
        }

        return result;
    }

    /**
     * Author Email is access only for admin gitlab user but search work for all users
     */
    public String getUsernameForRevision(String revision) {
        try {
            GitLabCommit commit = gitLabAPI.getGitLabAPICommits().getCommit(gitLabProject.getId(), revision);

            Paged<GitLabUser> paged = gitLabAPI.getGitLabAPIUsers().getUsers(commit.getAuthorEmail(), null);
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

    Set<Line> getPositionsFromPatch(String patch) {
        Set<Line> positions = new HashSet<>();

        int currentLine = -1;
        for (String line : patch.split("\n")) {
            if (line.startsWith("@")) {
                Matcher matcher = PATCH_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    throw new IllegalStateException("Unable to parse line:\n\t" + line + "\nFull patch: \n\t" + patch);
                }
                currentLine = Integer.parseInt(matcher.group(1));
            } else if (line.startsWith("+")) {
                positions.add(new Line(currentLine, line.replaceFirst("\\+", "")));
                currentLine++;
            } else if (line.startsWith(" ")) {
                // Can't comment line if not addition or deletion due to following bug
                // https://gitlab.com/gitlab-org/gitlab-ce/issues/26606
                currentLine++;
            }
        }

        return positions;
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

    public void createOrUpdateSonarQubeStatus(String status, String statusDescription) {
        try {
            gitLabAPI.getGitLabAPICommits().postCommitStatus(gitLabProject.getId(), getFirstCommitSHA(), status, config.refName(), COMMIT_CONTEXT, null, statusDescription);
        } catch (IOException e) {
            // Workaround for https://gitlab.com/gitlab-org/gitlab-ce/issues/25807
            if (e.getMessage() != null && e.getMessage().contains("Cannot transition status")) {
                LOG.debug("Transition status is already {}", status);
            } else {
                LOG.error("Unable to update commit status", e);
            }
        }
    }

    public boolean hasFile(InputFile inputFile) {
        String path = getPath(inputFile);
        for (String revision : config.commitSHA()) {
            if (patchPositionByFile.get(revision).containsKey(path)) {
                return true;
            }
        }
        return false;
    }

    public String getRevisionForLine(InputFile inputFile, int lineNumber) {
        String value = null;
        try {
            List<String> ss = Files.readAllLines(inputFile.path());
            value = ss.size() >= lineNumber ? ss.get(lineNumber - 1) : null;
        } catch (IOException e) {
            LOG.trace("Not read all line for file {}", inputFile.path(), e);
        }
        Line line = new Line(lineNumber, value);
        String path = getPath(inputFile);

        for (String revision : config.commitSHA()) {
            if (patchPositionByFile.get(revision).entrySet().stream().anyMatch(v ->
                    v.getKey().equals(path) && v.getValue().contains(line))) {
                return revision;
            }
        }
        return null;
    }

    @CheckForNull
    public String getGitLabUrl(@Nullable String revision, @Nullable InputComponent inputComponent, @Nullable Integer issueLine) {
        if (inputComponent instanceof InputPath) {
            String path = getPath((InputPath) inputComponent);
            return gitLabProject.getWebUrl() + "/blob/" + (revision != null ? revision : getFirstCommitSHA()) + "/" + path + (issueLine != null ? ("#L" + issueLine) : "");
        }
        return null;
    }

    public void createOrUpdateReviewComment(String revision, InputFile inputFile, Integer line, String body) {
        String fullPath = getPath(inputFile);
        try {
            gitLabAPI.getGitLabAPICommits().postCommitComments(gitLabProject.getId(), revision != null ? revision : getFirstCommitSHA(), body, fullPath, line, "new");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create or update review comment in file " + fullPath + " at line " + line, e);
        }
    }

    String getPath(InputPath inputPath) {
        return new PathResolver().relativePath(gitBaseDir, inputPath.file());
    }

    public void addGlobalComment(String comment) {
        try {
            gitLabAPI.getGitLabAPICommits().postCommitComments(gitLabProject.getId(), getFirstCommitSHA(), comment, null, null, null);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to comment the commit", e);
        }
    }

    private String getFirstCommitSHA() {
        return config.commitSHA() != null && !config.commitSHA().isEmpty() ? config.commitSHA().get(0) : null;
    }

    static class Line {

        private Integer number;

        private String content;

        Line(Integer number, String content) {
            this.number = number;
            this.content = content;
        }

        @Override
        public String toString() {
            return "Line{" + "number=" + number +
                    ", content='" + content + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Line line = (Line) o;
            return Objects.equals(number, line.number) &&
                    Objects.equals(content, line.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(number, content);
        }
    }
}
