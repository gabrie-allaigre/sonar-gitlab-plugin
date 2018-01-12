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

import com.talanlabs.sonar.plugins.gitlab.models.StatusNotificationsMode;
import org.sonar.api.batch.bootstrap.ProjectBuilder;

public class CommitProjectBuilder extends ProjectBuilder {

    private final GitLabPluginConfiguration gitLabPluginConfiguration;
    private final SonarFacade sonarFacade;
    private final CommitFacade commitFacade;

    public CommitProjectBuilder(GitLabPluginConfiguration gitLabPluginConfiguration, SonarFacade sonarFacade, CommitFacade commitFacade) {
        super();

        this.gitLabPluginConfiguration = gitLabPluginConfiguration;
        this.sonarFacade = sonarFacade;
        this.commitFacade = commitFacade;
    }

    @Override
    public void build(Context context) {
        if (!gitLabPluginConfiguration.isEnabled()) {
            return;
        }

        sonarFacade.init(context.projectReactor().getRoot().getBaseDir(), context.projectReactor().getRoot().getWorkDir());
        commitFacade.init(context.projectReactor().getRoot().getBaseDir());

        if (StatusNotificationsMode.COMMIT_STATUS.equals(gitLabPluginConfiguration.statusNotificationsMode())) {
            commitFacade.createOrUpdateSonarQubeStatus(gitLabPluginConfiguration.buildInitState().getMeaning(), "SonarQube analysis in progress");
        }
    }
}
