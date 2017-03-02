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
package com.talanlabs.sonar.plugins.gitlab;

import org.sonar.api.batch.bootstrap.ProjectBuilder;
import org.sonar.api.config.Settings;

/**
 * Trigger load of pull request metadata at the very beginning of SQ analysis. Also
 * set "in progress" status on the pull request.
 */
public class CommitProjectBuilder extends ProjectBuilder {

    private final GitLabPluginConfiguration gitLabPluginConfiguration;
    private final Settings settings;
    private final CommitFacade commitFacade;

    public CommitProjectBuilder(GitLabPluginConfiguration gitLabPluginConfiguration,CommitFacade commitFacade, Settings settings) {
        this.gitLabPluginConfiguration = gitLabPluginConfiguration;
        this.settings = settings;
        this.commitFacade = commitFacade;
    }

    @Override
    public void build(Context context) {
        if (!gitLabPluginConfiguration.isEnabled()) {
            return;
        }

        commitFacade.init(context.projectReactor().getRoot().getBaseDir());

        commitFacade.createOrUpdateSonarQubeStatus("pending", "SonarQube analysis in progress");
    }
}
