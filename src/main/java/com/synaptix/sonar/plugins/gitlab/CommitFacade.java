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

import org.apache.commons.io.IOUtils;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabCommitDiff;
import org.gitlab.api.models.GitlabProject;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.scan.filesystem.PathResolver;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Facade for all WS interaction with GitLab.
 */
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
@BatchSide
public class CommitFacade {

    static final String COMMIT_CONTEXT = "sonarqube";

    private final GitLabPluginConfiguration config;
    private File gitBaseDir;
    private GitlabAPI gitLabAPI;
    private GitlabProject gitLabProject;
    private Map<String, Set<Integer>> patchPositionMappingByFile;

    public CommitFacade(GitLabPluginConfiguration config) {
        this.config = config;
    }

    private static Map<String, Set<Integer>> mapPatchPositionsToLines(List<GitlabCommitDiff> diffs) throws IOException {
        Map<String, Set<Integer>> patchPositionMappingByFile = new HashMap<>();
        for (GitlabCommitDiff file : diffs) {
            Set<Integer> patchLocationMapping = new HashSet<>();
            patchPositionMappingByFile.put(file.getNewPath(), patchLocationMapping);
            String patch = file.getDiff();
            if (patch == null) {
                continue;
            }
            processPatch(patchLocationMapping, patch);
        }
        return patchPositionMappingByFile;
    }
    
    // http://en.wikipedia.org/wiki/Diff_utility#Unified_format
    private static final Pattern PATCH_PATTERN = Pattern.compile("@@\\p{Space}-[0-9]+(?:,[0-9]+)?\\p{Space}\\+([0-9]+)(?:,[0-9]+)?\\p{Space}@@.*");

    private static void processPatch(Set<Integer> patchLocationMapping, String patch) throws IOException {
        int currentLine = -1;
        for (String line : IOUtils.readLines(new StringReader(patch))) {
            if (line.startsWith("@")) {
                Matcher matcher = PATCH_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    throw new IllegalStateException("Unable to parse patch line " + line + "\nFull patch: \n" + patch);
                }
                currentLine = Integer.parseInt(matcher.group(1));
            } else if (line.startsWith("-")) {
                // Skip removed lines
            } else if (line.startsWith("+") || line.startsWith(" ")) {
                // Count added and unmodified lines
                patchLocationMapping.add(currentLine);
                currentLine++;
            } else if (line.startsWith("\\")) {
                // I'm only aware of \ No newline at end of file
                // Ignore
            }
        }
    }

    public void init(File projectBaseDir) {
        if (findGitBaseDir(projectBaseDir) == null) {
            throw new IllegalStateException("Unable to find Git root directory. Is " + projectBaseDir + " part of a Git repository?");
        }
        gitLabAPI = GitlabAPI.connect(config.url(), config.userToken()).ignoreCertificateErrors(config.ignoreCertificate());
        try {
            gitLabProject = getGitlabProject();
            List<GitlabCommitDiff> commitDiffs = gitLabAPI.getCommitDiffs(gitLabProject.getId(), config.commitSHA());
            patchPositionMappingByFile = mapPatchPositionsToLines(commitDiffs);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to perform Gitlab WS operation", e);
        }
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

    private GitlabProject getGitlabProject() throws IOException {
        if (config.projectId() == null) {
            throw new IllegalStateException("Unable found project for null project name. Set Configuration sonar.gitlab.project_id");
        }
        List<GitlabProject> res = new ArrayList<>();
        for (GitlabProject project : gitLabAPI.getProjects()) {
            if (config.projectId().equals(project.getId().toString()) || config.projectId().equals(project.getPathWithNamespace()) || config.projectId().equals(project.getHttpUrl()) || config
                    .projectId().equals(project.getSshUrl()) || config.projectId().equals(project.getWebUrl()) || config.projectId().equals(project.getNameWithNamespace())) {
                res.add(project);
            }
        }
        if (res.isEmpty()) {
            throw new IllegalStateException("Unable found project for " + config.projectId() + " Verify Configuration sonar.gitlab.project_id or sonar.gitlab.user_token access project");
        }
        if (res.size() > 1) {
            throw new IllegalStateException("Multiple found projects for " + config.projectId());
        }
        return res.get(0);
    }

    public void createOrUpdateSonarQubeStatus(String status, String statusDescription) {
        try {
            gitLabAPI.createCommitStatus(gitLabProject, config.commitSHA(), status, config.refName(), COMMIT_CONTEXT, null, statusDescription);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to update commit status", e);
        }
    }

    public boolean hasFile(InputFile inputFile) {
        return patchPositionMappingByFile.containsKey(getPath(inputFile));
    }

    public boolean hasFileLine(InputFile inputFile, int line) {
        return hasFile(inputFile) && patchPositionMappingByFile.get(getPath(inputFile)).contains(line);
    }

    public String getGitLabUrl(InputFile inputFile, Integer issueLine) {
        if (inputFile != null) {
            String path = getPath(inputFile);
            return gitLabProject.getWebUrl() + "/blob/" + config.commitSHA() + "/" + path + (issueLine != null ? ("#L" + issueLine) : "");
        }
        return null;
    }

    public void createOrUpdateReviewComment(InputFile inputFile, Integer line, String body) {
        String fullPath = getPath(inputFile);
        try {
            gitLabAPI.createCommitComment(gitLabProject.getId(), config.commitSHA(), body, fullPath, String.valueOf(line), "new");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create or update review comment in file " + fullPath + " at line " + line, e);
        }
    }

    private String getPath(InputPath inputPath) {
        return new PathResolver().relativePath(gitBaseDir, inputPath.file());
    }

    public void addGlobalComment(String comment) {
        try {
            gitLabAPI.createCommitComment(gitLabProject.getId(), config.commitSHA(), comment, null, null, null);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to comment the commit", e);
        }
    }
}
