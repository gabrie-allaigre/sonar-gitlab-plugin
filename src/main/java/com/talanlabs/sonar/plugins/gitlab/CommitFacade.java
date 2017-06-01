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

/**
 * Facade for all WS interaction with GitLab.
 */
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
@BatchSide
public class CommitFacade {

    private static final Logger LOG = Loggers.get(CommitFacade.class);

    private final GitLabPluginConfiguration config;
    private File gitBaseDir;

    private IGitLabApiWrapper gitLabWrapper;

    public CommitFacade(GitLabPluginConfiguration config) {
        this.config = config;

        if (GitLabPlugin.V3_API_VERSION.equals(config.apiVersion())) {
            this.gitLabWrapper = new GitLabApiV3Wrapper(config);
        } else if (GitLabPlugin.V4_API_VERSION.equals(config.apiVersion())) {
            this.gitLabWrapper = new GitLabApiV4Wrapper(config);
        }
    }

    public void init(File projectBaseDir) {
        initGitBaseDir(projectBaseDir);

        gitLabWrapper.init();
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

    public boolean hasSameCommitCommentsForFile(String revision, InputFile inputFile, Integer lineNumber, String body) {
        String path = getPath(inputFile);
        return gitLabWrapper.hasSameCommitCommentsForFile(revision, path, lineNumber, body);
    }

    /**
     * Author Email is access only for admin gitlab user but search work for all users
     */
    public String getUsernameForRevision(String revision) {
        return gitLabWrapper.getUsernameForRevision(revision);
    }

    public void createOrUpdateSonarQubeStatus(String status, String statusDescription) {
        gitLabWrapper.createOrUpdateSonarQubeStatus(status, statusDescription);
    }

    public boolean hasFile(InputFile inputFile) {
        String path = getPath(inputFile);
        return gitLabWrapper.hasFile(path);
    }

    public String getRevisionForLine(InputFile inputFile, int lineNumber) {
        String path = getPath(inputFile);
        return gitLabWrapper.getRevisionForLine(inputFile, path, lineNumber);
    }

    @CheckForNull
    public String getGitLabUrl(@Nullable String revision, @Nullable InputComponent inputComponent, @Nullable Integer issueLine) {
        if (inputComponent instanceof InputPath) {
            String path = getPath((InputPath) inputComponent);
            return gitLabWrapper.getGitLabUrl(revision, path, issueLine);
        }
        return null;
    }

    public void createOrUpdateReviewComment(String revision, InputFile inputFile, Integer line, String body) {
        String fullPath = getPath(inputFile);
        gitLabWrapper.createOrUpdateReviewComment(revision, fullPath, line, body);
    }

    String getPath(InputPath inputPath) {
        String prefix = config.prefixDirectory() != null ? config.prefixDirectory() : "";
        return prefix + new PathResolver().relativePath(gitBaseDir, inputPath.file());
    }

    public void addGlobalComment(String comment) {
        gitLabWrapper.addGlobalComment(comment);
    }
}
