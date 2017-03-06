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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.AnalysisMode;
import org.sonar.api.batch.bootstrap.ProjectBuilder;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.System2;

import java.io.File;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class CommitProjectBuilderTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private CommitProjectBuilder commitProjectBuilder;
    private CommitFacade facade;
    private Settings settings;
    private AnalysisMode mode;

    @Before
    public void prepare() {
        settings = new Settings(new PropertyDefinitions(GitLabPlugin.definitions()));
        facade = mock(CommitFacade.class);
        mode = mock(AnalysisMode.class);
        commitProjectBuilder = new CommitProjectBuilder(new GitLabPluginConfiguration(settings, new System2()), facade, mode);

    }

    @Test
    public void testShouldDoNothing() {
        commitProjectBuilder.build(null);
        verifyZeroInteractions(facade);
    }

    @Test
    public void shouldFailIfNotPreview() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "1");

        thrown.expect(MessageException.class);
        thrown.expectMessage("The GitLab plugin is only intended to be used in preview or issues mode. Please set 'sonar.analysis.mode'.");

        commitProjectBuilder.build(null);
    }

    @Test
    public void shouldNotFailIfIssuesPending() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "1");
        when(mode.isIssues()).thenReturn(true);

        commitProjectBuilder.build(mock(ProjectBuilder.Context.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS)));

        verify(facade).init(any(File.class));
        verify(facade).createOrUpdateSonarQubeStatus(BuildInitState.PENDING.getMeaning(), "SonarQube analysis in progress");
    }

    @Test
    public void shouldNotFailIfIssuesRunning() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "1");
        settings.setProperty(GitLabPlugin.GITLAB_BUILD_INIT_STATE, BuildInitState.RUNNING.getMeaning());

        when(mode.isIssues()).thenReturn(true);

        commitProjectBuilder.build(mock(ProjectBuilder.Context.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS)));

        verify(facade).init(any(File.class));
        verify(facade).createOrUpdateSonarQubeStatus(BuildInitState.RUNNING.getMeaning(), "SonarQube analysis in progress");
    }

    @Test
    public void shouldNotFailIfIssuesNone() {
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "1");
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NOTIFICATION_MODE, StatusNotificationsMode.EXIT_CODE.getMeaning());

        when(mode.isIssues()).thenReturn(true);

        commitProjectBuilder.build(mock(ProjectBuilder.Context.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS)));

        verify(facade).init(any(File.class));
        verify(facade, never()).createOrUpdateSonarQubeStatus(BuildInitState.PENDING.getMeaning(), "SonarQube analysis in progress");
    }
}
