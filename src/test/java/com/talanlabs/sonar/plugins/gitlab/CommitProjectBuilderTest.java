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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.bootstrap.ProjectBuilder;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.System2;

import java.io.File;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class CommitProjectBuilderTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private CommitProjectBuilder commitProjectBuilder;
    private SonarFacade sonarFacade;
    private CommitFacade commitFacade;
    private MapSettings settings;

    @Before
    public void prepare() {
        settings = new MapSettings(new PropertyDefinitions(GitLabPlugin.definitions()));
        sonarFacade = mock(SonarFacade.class);
        commitFacade = mock(CommitFacade.class);
        commitProjectBuilder = new CommitProjectBuilder(new GitLabPluginConfiguration(settings.asConfig(), new System2()), sonarFacade, commitFacade);
    }

    @Test
    public void testShouldDoNothing() {
        commitProjectBuilder.build(null);
        verifyZeroInteractions(commitFacade);
    }

    @Test
    public void testExitCode() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "1");
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());

        commitProjectBuilder.build(mock(ProjectBuilder.Context.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS)));

        verify(sonarFacade).init(any(File.class), any(File.class));
        verify(commitFacade).init(any(File.class));
        verify(commitFacade, never()).createOrUpdateSonarQubeStatus(BuildInitState.PENDING.getMeaning(), "SonarQube analysis in progress");
        verify(commitFacade, never()).createOrUpdateSonarQubeStatus(BuildInitState.RUNNING.getMeaning(), "SonarQube analysis in progress");
    }

    @Test
    public void testNothing() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "1");
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.NOTHING.getMeaning());

        commitProjectBuilder.build(mock(ProjectBuilder.Context.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS)));

        verify(sonarFacade).init(any(File.class), any(File.class));
        verify(commitFacade).init(any(File.class));
        verify(commitFacade, never()).createOrUpdateSonarQubeStatus(BuildInitState.PENDING.getMeaning(), "SonarQube analysis in progress");
        verify(commitFacade, never()).createOrUpdateSonarQubeStatus(BuildInitState.RUNNING.getMeaning(), "SonarQube analysis in progress");
    }

    @Test
    public void testCommitStatusPending() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "1");
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.COMMIT_STATUS.getMeaning());

        commitProjectBuilder.build(mock(ProjectBuilder.Context.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS)));

        verify(sonarFacade).init(any(File.class), any(File.class));
        verify(commitFacade).init(any(File.class));
        verify(commitFacade).createOrUpdateSonarQubeStatus(BuildInitState.PENDING.getMeaning(), "SonarQube analysis in progress");
    }

    @Test
    public void testCommitStatusRunning() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "1");
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.COMMIT_STATUS.getMeaning());
        settings.setProperty(GitLabPlugin.GITLAB_BUILD_INIT_STATE, BuildInitState.RUNNING.getMeaning());

        commitProjectBuilder.build(mock(ProjectBuilder.Context.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS)));

        verify(sonarFacade).init(any(File.class), any(File.class));
        verify(commitFacade).init(any(File.class));
        verify(commitFacade).createOrUpdateSonarQubeStatus(BuildInitState.RUNNING.getMeaning(), "SonarQube analysis in progress");
    }
}
