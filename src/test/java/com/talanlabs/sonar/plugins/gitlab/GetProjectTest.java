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