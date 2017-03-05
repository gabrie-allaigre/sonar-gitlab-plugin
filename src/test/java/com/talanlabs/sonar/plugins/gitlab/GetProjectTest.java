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

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetProjectTest {

    @Rule
    public MockWebServer gitlab = new MockWebServer();
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    GitLabPluginConfiguration gitLabPluginConfiguration;

    @Before
    public void before() {
        gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.url()).thenReturn(String.format("http://%s:%d", gitlab.getHostName(), gitlab.getPort()));
        when(gitLabPluginConfiguration.userToken()).thenReturn("123456789");
    }

    @Test
    public void testProjectIdNull() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn(null);

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        Assertions.assertThatThrownBy(() -> facade.init(gitBasedir)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unable found project for null project name. Set Configuration sonar.gitlab.project_id");
    }

    @Test
    public void testProjectIdReturnEmpty() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("123");

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[{\n" +
                "    \"id\": 4,\n" +
                "    \"description\": null,\n" +
                "    \"default_branch\": \"master\",\n" +
                "    \"visibility\": \"private\",\n" +
                "    \"ssh_url_to_repo\": \"git@example.com:diaspora/diaspora-client.git\",\n" +
                "    \"http_url_to_repo\": \"http://example.com/diaspora/diaspora-client.git\",\n" +
                "    \"web_url\": \"http://example.com/diaspora/diaspora-client\",\n" +
                "\t\"name\": \"Diaspora Client\",\n" +
                "    \"name_with_namespace\": \"Diaspora / Diaspora Client\",\n" +
                "    \"path\": \"diaspora-client\",\n" +
                "    \"path_with_namespace\": \"diaspora/diaspora-client\"\n" +
                "}]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        Assertions.assertThatThrownBy(() -> facade.init(gitBasedir)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unable found project for 123 Verify Configuration sonar.gitlab.project_id or sonar.gitlab.user_token access project");
    }

    @Test
    public void testProjectIdReturnNotFound() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("123");

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        Assertions.assertThatThrownBy(() -> facade.init(gitBasedir)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unable found project for 123 Verify Configuration sonar.gitlab.project_id or sonar.gitlab.user_token access project");
    }

    @Test
    public void testProjectIdReturnMultiple() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("4");

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[{\n" +
                "    \"id\": 4,\n" +
                "    \"description\": null,\n" +
                "    \"default_branch\": \"master\",\n" +
                "    \"visibility\": \"private\",\n" +
                "    \"ssh_url_to_repo\": \"git@example.com:diaspora/diaspora-client.git\",\n" +
                "    \"http_url_to_repo\": \"http://example.com/diaspora/diaspora-client.git\",\n" +
                "    \"web_url\": \"http://example.com/diaspora/diaspora-client\",\n" +
                "\t\"name\": \"Diaspora Client\",\n" +
                "    \"name_with_namespace\": \"Diaspora / Diaspora Client\",\n" +
                "    \"path\": \"diaspora-client\",\n" +
                "    \"path_with_namespace\": \"diaspora/diaspora-client\"\n" +
                "},{\n" +
                "    \"id\": 4,\n" +
                "    \"description\": null,\n" +
                "    \"default_branch\": \"master\",\n" +
                "    \"visibility\": \"private\",\n" +
                "    \"ssh_url_to_repo\": \"git@example.com:diaspora/diaspora-client.git\",\n" +
                "    \"http_url_to_repo\": \"http://example.com/diaspora/diaspora-client.git\",\n" +
                "    \"web_url\": \"http://example.com/diaspora/diaspora-client\",\n" +
                "\t\"name\": \"Diaspora Client\",\n" +
                "    \"name_with_namespace\": \"Diaspora / Diaspora Client\",\n" +
                "    \"path\": \"diaspora-client\",\n" +
                "    \"path_with_namespace\": \"diaspora/diaspora-client\"\n" +
                "}]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        Assertions.assertThatThrownBy(() -> facade.init(gitBasedir)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Multiple found projects for 4");
    }

    @Test
    public void testProjectIdWithId() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("123");

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[{\n" +
                "    \"id\": 123,\n" +
                "    \"description\": null,\n" +
                "    \"default_branch\": \"master\",\n" +
                "    \"visibility\": \"private\",\n" +
                "    \"ssh_url_to_repo\": \"git@example.com:diaspora/diaspora-client.git\",\n" +
                "    \"http_url_to_repo\": \"http://example.com/diaspora/diaspora-client.git\",\n" +
                "    \"web_url\": \"http://example.com/diaspora/diaspora-client\",\n" +
                "\t\"name\": \"Diaspora Client\",\n" +
                "    \"name_with_namespace\": \"Diaspora / Diaspora Client\",\n" +
                "    \"path\": \"diaspora-client\",\n" +
                "    \"path_with_namespace\": \"diaspora/diaspora-client\"\n" +
                "}]"));

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        facade.init(gitBasedir);
    }

    @Test
    public void testProjectIdWithSshUrl() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("git@example.com:diaspora/diaspora-client.git");

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[{\n" +
                "    \"id\": 123,\n" +
                "    \"description\": null,\n" +
                "    \"default_branch\": \"master\",\n" +
                "    \"visibility\": \"private\",\n" +
                "    \"ssh_url_to_repo\": \"git@example.com:diaspora/diaspora-client.git\",\n" +
                "    \"http_url_to_repo\": \"http://example.com/diaspora/diaspora-client.git\",\n" +
                "    \"web_url\": \"http://example.com/diaspora/diaspora-client\",\n" +
                "\t\"name\": \"Diaspora Client\",\n" +
                "    \"name_with_namespace\": \"Diaspora / Diaspora Client\",\n" +
                "    \"path\": \"diaspora-client\",\n" +
                "    \"path_with_namespace\": \"diaspora/diaspora-client\"\n" +
                "}]"));

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        facade.init(gitBasedir);
    }

    @Test
    public void testProjectIdWithHttpUrl() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("http://example.com/diaspora/diaspora-client.git");

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[{\n" +
                "    \"id\": 123,\n" +
                "    \"description\": null,\n" +
                "    \"default_branch\": \"master\",\n" +
                "    \"visibility\": \"private\",\n" +
                "    \"ssh_url_to_repo\": \"git@example.com:diaspora/diaspora-client.git\",\n" +
                "    \"http_url_to_repo\": \"http://example.com/diaspora/diaspora-client.git\",\n" +
                "    \"web_url\": \"http://example.com/diaspora/diaspora-client\",\n" +
                "\t\"name\": \"Diaspora Client\",\n" +
                "    \"name_with_namespace\": \"Diaspora / Diaspora Client\",\n" +
                "    \"path\": \"diaspora-client\",\n" +
                "    \"path_with_namespace\": \"diaspora/diaspora-client\"\n" +
                "}]"));

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        facade.init(gitBasedir);
    }

    @Test
    public void testProjectIdWithWebUrl() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("http://example.com/diaspora/diaspora-client");

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[{\n" +
                "    \"id\": 123,\n" +
                "    \"description\": null,\n" +
                "    \"default_branch\": \"master\",\n" +
                "    \"visibility\": \"private\",\n" +
                "    \"ssh_url_to_repo\": \"git@example.com:diaspora/diaspora-client.git\",\n" +
                "    \"http_url_to_repo\": \"http://example.com/diaspora/diaspora-client.git\",\n" +
                "    \"web_url\": \"http://example.com/diaspora/diaspora-client\",\n" +
                "\t\"name\": \"Diaspora Client\",\n" +
                "    \"name_with_namespace\": \"Diaspora / Diaspora Client\",\n" +
                "    \"path\": \"diaspora-client\",\n" +
                "    \"path_with_namespace\": \"diaspora/diaspora-client\"\n" +
                "}]"));

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        facade.init(gitBasedir);
    }

    @Test
    public void testProjectIdWithPath() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("diaspora/diaspora-client");

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[{\n" +
                "    \"id\": 123,\n" +
                "    \"description\": null,\n" +
                "    \"default_branch\": \"master\",\n" +
                "    \"visibility\": \"private\",\n" +
                "    \"ssh_url_to_repo\": \"git@example.com:diaspora/diaspora-client.git\",\n" +
                "    \"http_url_to_repo\": \"http://example.com/diaspora/diaspora-client.git\",\n" +
                "    \"web_url\": \"http://example.com/diaspora/diaspora-client\",\n" +
                "\t\"name\": \"Diaspora Client\",\n" +
                "    \"name_with_namespace\": \"Diaspora / Diaspora Client\",\n" +
                "    \"path\": \"diaspora-client\",\n" +
                "    \"path_with_namespace\": \"diaspora/diaspora-client\"\n" +
                "}]"));

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        facade.init(gitBasedir);
    }

    @Test
    public void testProjectIdWithName() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("Diaspora / Diaspora Client");

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[{\n" +
                "    \"id\": 123,\n" +
                "    \"description\": null,\n" +
                "    \"default_branch\": \"master\",\n" +
                "    \"visibility\": \"private\",\n" +
                "    \"ssh_url_to_repo\": \"git@example.com:diaspora/diaspora-client.git\",\n" +
                "    \"http_url_to_repo\": \"http://example.com/diaspora/diaspora-client.git\",\n" +
                "    \"web_url\": \"http://example.com/diaspora/diaspora-client\",\n" +
                "\t\"name\": \"Diaspora Client\",\n" +
                "    \"name_with_namespace\": \"Diaspora / Diaspora Client\",\n" +
                "    \"path\": \"diaspora-client\",\n" +
                "    \"path_with_namespace\": \"diaspora/diaspora-client\"\n" +
                "}]"));

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration);
        facade.init(gitBasedir);
    }
}