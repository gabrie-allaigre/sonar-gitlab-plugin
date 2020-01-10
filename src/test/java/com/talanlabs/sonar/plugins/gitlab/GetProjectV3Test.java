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

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.talanlabs.gitlab.api.v3.GitLabAPI;
import com.talanlabs.gitlab.api.v3.models.projects.GitLabProject;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetProjectV3Test {

    @Rule
    public MockWebServer gitlab = new MockWebServer();
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private GitLabPluginConfiguration gitLabPluginConfiguration;
    private SonarFacade sonarFacade;

    @Before
    public void before() {
        gitLabPluginConfiguration = mock(GitLabPluginConfiguration.class);
        when(gitLabPluginConfiguration.url()).thenReturn(String.format("http://%s:%d", gitlab.getHostName(), gitlab.getPort()));
        when(gitLabPluginConfiguration.userToken()).thenReturn("123456789");
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Collections.singletonList("123456789"));
        when(gitLabPluginConfiguration.apiVersion()).thenReturn(GitLabPlugin.V3_API_VERSION);

        sonarFacade = mock(SonarFacade.class);
    }

    @Test
    public void testProjectIdNull() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn(null);

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration, sonarFacade);
        Assertions.assertThatThrownBy(() -> facade.init(gitBasedir)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unable to find project ID null. Set the property sonar.gitlab.project_id");
    }

    @Test
    public void testProjectIdReturnEmpty() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("123");

        gitlab.enqueue(new MockResponse().setResponseCode(404));
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

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration, sonarFacade);
        Assertions.assertThatThrownBy(() -> facade.init(gitBasedir)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unable to find project ID 123. Either the project ID is incorrect or you don't have access to this project. Verify the configurations sonar.gitlab.project_id or sonar.gitlab.user_token");
    }

    @Test
    public void testProjectIdReturnNotFound() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("123");

        gitlab.enqueue(new MockResponse().setResponseCode(404));
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration, sonarFacade);
        Assertions.assertThatThrownBy(() -> facade.init(gitBasedir)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unable to find project ID 123. Either the project ID is incorrect or you don't have access to this project. Verify the configurations sonar.gitlab.project_id or sonar.gitlab.user_token");
    }

    @Test
    public void testProjectIdReturnMultiple() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("4");

        gitlab.enqueue(new MockResponse().setResponseCode(404));
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

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration, sonarFacade);
        Assertions.assertThatThrownBy(() -> facade.init(gitBasedir)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Multiple found projects for 4");
    }

    @Test
    public void testProjectIdWithId() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("123");

        gitlab.enqueue(new MockResponse().setResponseCode(404));
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
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration, sonarFacade);
        facade.init(gitBasedir);
    }

    @Test
    public void testProjectIdWithSshUrl() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("git@example.com:diaspora/diaspora-client.git");

        gitlab.enqueue(new MockResponse().setResponseCode(404));
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
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration, sonarFacade);
        facade.init(gitBasedir);
    }

    @Test
    public void testProjectIdWithHttpUrl() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("http://example.com/diaspora/diaspora-client.git");

        gitlab.enqueue(new MockResponse().setResponseCode(404));
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
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration, sonarFacade);
        facade.init(gitBasedir);
    }

    @Test
    public void testProjectIdWithWebUrl() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("http://example.com/diaspora/diaspora-client");

        gitlab.enqueue(new MockResponse().setResponseCode(404));
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
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration, sonarFacade);
        facade.init(gitBasedir);
    }

    @Test
    public void testProjectIdWithPath() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("diaspora/diaspora-client");

        gitlab.enqueue(new MockResponse().setResponseCode(404));
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
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration, sonarFacade);
        facade.init(gitBasedir);
    }

    @Test
    public void testProjectIdWithName() throws Exception {
        File gitBasedir = temp.newFolder();

        when(gitLabPluginConfiguration.projectId()).thenReturn("Diaspora / Diaspora Client");

        gitlab.enqueue(new MockResponse().setResponseCode(404));
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
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration, sonarFacade);
        facade.init(gitBasedir);
    }

    @Test
    public void testInitProject() throws Exception {
        File gitBasedir = temp.newFolder();
        Utils.createFile(gitBasedir, "src/main/java/com/talanlabs/sonar/plugins/gitlab", "Fake.java", "package com.talanlabs.sonar.plugins.gitlab;\n" +
                "\n" +
                "import java.util.List;\n" +
                "\n" +
                "public class Fake {\n" +
                "\n" +
                "    List<String> ss;\n" +
                "\n" +
                "    public Fake(List<String> ss) {\n" +
                "        this.ss = ss;\n" +
                "    }\n" +
                "\n" +
                "    public void fonction() {\n" +
                "        String toto = null;\n" +
                "        System.out.println(toto.length());\n" +
                "    }\n" +
                "\n" +
                "\n" +
                "}");
        Utils.createFile(gitBasedir, "src/main/java/com/talanlabs/sonar/plugins/gitlab", "Fake2.java", "package com.talanlabs.sonar.plugins.gitlab;\n" +
                "\n" +
                "import java.util.List;\n" +
                "\n" +
                "public class Fake2 {\n" +
                "\n" +
                "    List<String> ss;\n" +
                "\n" +
                "    public Fake(List<String> ss) {\n" +
                "        this.ss = ss;\n" +
                "    }\n" +
                "\n" +
                "    public void fonction() {\n" +
                "        String toto = null;\n" +
                "        System.out.println(toto.length());\n" +
                "    }\n" +
                "\n" +
                "\n" +
                "}");

        when(gitLabPluginConfiguration.projectId()).thenReturn("123");
        when(gitLabPluginConfiguration.commitSHA()).thenReturn(Arrays.asList("123", "456"));

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("{\n" +
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
                "}"));

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[\n" +
                "  {\n" +
                "    \"note\": \"test\",\n" +
                "    \"path\": \"src/main/java/com/talanlabs/sonar/plugins/gitlab/Fake.java\",\n" +
                "    \"line\": 7,\n" +
                "    \"line_type\": \"new\",\n" +
                "    \"author\": {\n" +
                "      \"name\": \"Gabriel Allaigre\",\n" +
                "      \"username\": \"gabriel-allaigre\",\n" +
                "      \"id\": 7,\n" +
                "      \"state\": \"active\",\n" +
                "      \"avatar_url\": \"https://gitlab.talanlabs.com/uploads/user/avatar/7/Gaby_manga.png\",\n" +
                "      \"web_url\": \"https://gitlab.talanlabs.com/gabriel-allaigre\"\n" +
                "    },\n" +
                "    \"created_at\": \"2017-03-17T09:51:30.135Z\"\n" +
                "  }\n" +
                "]"));
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[\n" +
                "  {\n" +
                "    \"note\": \"test\",\n" +
                "    \"path\": \"src/main/java/com/talanlabs/sonar/plugins/gitlab/Fake2.java\",\n" +
                "    \"line\": 10,\n" +
                "    \"line_type\": \"new\",\n" +
                "    \"author\": {\n" +
                "      \"name\": \"Gabriel Allaigre\",\n" +
                "      \"username\": \"gabriel-allaigre\",\n" +
                "      \"id\": 7,\n" +
                "      \"state\": \"active\",\n" +
                "      \"avatar_url\": \"https://gitlab.talanlabs.com/uploads/user/avatar/7/Gaby_manga.png\",\n" +
                "      \"web_url\": \"https://gitlab.talanlabs.com/gabriel-allaigre\"\n" +
                "    },\n" +
                "    \"created_at\": \"2017-03-17T09:51:30.135Z\"\n" +
                "  }\n" +
                "]"));
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[\n" +
                "  {\n" +
                "    \"diff\": \"--- /dev/null\\n+++ b/src/main/java/com/talanlabs/sonar/plugins/gitlab/Fake.java\\n@@ -0,0 +1,19 @@\\n+package com.talanlabs.sonar.plugins.gitlab;\\n+\\n+import java.util.List;\\n+\\n+public class Fake {\\n+\\n+    List<String> ss;\\n+\\n+    public Fake(List<String> ss) {\\n+        this.ss = ss;\\n+    }\\n+\\n+    public void fonction() {\\n+        String toto = null;\\n+        System.out.println(toto.length());\\n+    }\\n+\\n+\\n+}\\n\",\n" +
                "    \"new_path\": \"src/main/java/com/talanlabs/sonar/plugins/gitlab/Fake.java\",\n" +
                "    \"old_path\": \"src/main/java/com/talanlabs/sonar/plugins/gitlab/Fake.java\",\n" +
                "    \"a_mode\": \"0\",\n" +
                "    \"b_mode\": \"100644\",\n" +
                "    \"new_file\": true,\n" +
                "    \"renamed_file\": false,\n" +
                "    \"deleted_file\": false,\n" +
                "    \"too_large\": null\n" +
                "  }\n" +
                "]"));
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[\n" +
                "  {\n" +
                "    \"diff\": \"--- /dev/null\\n+++ b/src/main/java/com/talanlabs/sonar/plugins/gitlab/Fake2.java\\n@@ -0,0 +1,19 @@\\n+package com.talanlabs.sonar.plugins.gitlab;\\n+\\n+import java.util.List;\\n+\\n+public class Fake2 {\\n+\\n+    List<String> ss;\\n+\\n+    public Fake(List<String> ss) {\\n+        this.ss = ss;\\n+    }\\n+\\n+    public void fonction() {\\n+        String toto = null;\\n+        System.out.println(toto.length());\\n+    }\\n+\\n+\\n+}\\n\",\n" +
                "    \"new_path\": \"src/main/java/com/talanlabs/sonar/plugins/gitlab/Fake2.java\",\n" +
                "    \"old_path\": \"src/main/java/com/talanlabs/sonar/plugins/gitlab/Fake2.java\",\n" +
                "    \"a_mode\": \"0\",\n" +
                "    \"b_mode\": \"100644\",\n" +
                "    \"new_file\": true,\n" +
                "    \"renamed_file\": false,\n" +
                "    \"deleted_file\": false,\n" +
                "    \"too_large\": null\n" +
                "  }\n" +
                "]"));

        File inputFile1 = new File(gitBasedir, "src/Foo2.php");
        File inputFile2 = new File(gitBasedir, "src/main/java/com/talanlabs/sonar/plugins/gitlab/Fake.java");
        File inputFile3 = new File(gitBasedir, "src/main/java/com/talanlabs/sonar/plugins/gitlab/Fake2.java");

        CommitFacade facade = new CommitFacade(gitLabPluginConfiguration, sonarFacade);
        facade.init(gitBasedir);

        Assertions.assertThat(facade.hasFile(inputFile1)).isFalse();
        Assertions.assertThat(facade.hasFile(inputFile2)).isTrue();
        Assertions.assertThat(facade.hasFile(inputFile3)).isTrue();

        Assertions.assertThat(facade.getRevisionForLine(inputFile1, 100)).isNull();
        Assertions.assertThat(facade.getRevisionForLine(inputFile2, 1)).isEqualTo("123");
        Assertions.assertThat(facade.getRevisionForLine(inputFile3, 100)).isNull();
        Assertions.assertThat(facade.getRevisionForLine(inputFile3, 3)).isEqualTo("456");

        Assertions.assertThat(facade.hasSameCommitCommentsForFile("123", inputFile1, 1, "test")).isFalse();
        Assertions.assertThat(facade.hasSameCommitCommentsForFile("123", inputFile2, 7, "test")).isTrue();
        Assertions.assertThat(facade.hasSameCommitCommentsForFile("456", inputFile2, 7, "toto")).isFalse();
        Assertions.assertThat(facade.hasSameCommitCommentsForFile("456", inputFile3, 10, "test")).isTrue();
    }

    @Test
    public void testGetUsernameForRevisionNull1() throws Exception {
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("{\n" +
                "  \"id\": \"6104942438c14ec7bd21c6cd5bd995272b3faff6\",\n" +
                "  \"short_id\": \"6104942438c\",\n" +
                "  \"title\": \"Sanitize for network graph\",\n" +
                "  \"author_name\": \"randx\",\n" +
                "  \"author_email\": \"dmitriy.zaporozhets@gmail.com\",\n" +
                "  \"committer_name\": \"Dmitriy\",\n" +
                "  \"committer_email\": \"dmitriy.zaporozhets@gmail.com\",\n" +
                "  \"created_at\": \"2012-09-20T09:06:12+03:00\",\n" +
                "  \"message\": \"Sanitize for network graph\",\n" +
                "  \"committed_date\": \"2012-09-20T09:06:12+03:00\",\n" +
                "  \"authored_date\": \"2012-09-20T09:06:12+03:00\",\n" +
                "  \"parent_ids\": [\n" +
                "    \"ae1d9fb46aa2b07ee9836d49862ec4e2c46fbbba\"\n" +
                "  ],\n" +
                "  \"stats\": {\n" +
                "    \"additions\": 15,\n" +
                "    \"deletions\": 10,\n" +
                "    \"total\": 25\n" +
                "  },\n" +
                "  \"status\": \"running\"\n" +
                "}"));

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[\n" +
                "  {\n" +
                "    \"id\": 1,\n" +
                "    \"username\": \"john_smith\",\n" +
                "    \"name\": \"John Smith\",\n" +
                "    \"state\": \"active\",\n" +
                "    \"avatar_url\": \"http://localhost:3000/uploads/user/avatar/1/cd8.jpeg\",\n" +
                "    \"web_url\": \"http://localhost:3000/john_smith\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 2,\n" +
                "    \"username\": \"jack_smith\",\n" +
                "    \"name\": \"Jack Smith\",\n" +
                "    \"state\": \"blocked\",\n" +
                "    \"avatar_url\": \"http://gravatar.com/../e32131cd8.jpeg\",\n" +
                "    \"web_url\": \"http://localhost:3000/jack_smith\"\n" +
                "  }\n" +
                "]"));


        GitLabApiV3Wrapper facade = new GitLabApiV3Wrapper(gitLabPluginConfiguration, sonarFacade);
        facade.setGitLabAPI(GitLabAPI.connect(gitLabPluginConfiguration.url(), gitLabPluginConfiguration.userToken()));
        GitLabProject gitLabProject = Mockito.mock(GitLabProject.class);
        Mockito.when(gitLabProject.getId()).thenReturn(1);
        facade.setGitLabProject(gitLabProject);

        Assertions.assertThat(facade.getUsernameForRevision("123")).isNull();
    }

    @Test
    public void testGetUsernameForRevisionNull2() throws Exception {
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("{\n" +
                "  \"id\": \"6104942438c14ec7bd21c6cd5bd995272b3faff6\",\n" +
                "  \"short_id\": \"6104942438c\",\n" +
                "  \"title\": \"Sanitize for network graph\",\n" +
                "  \"author_name\": \"randx\",\n" +
                "  \"author_email\": \"dmitriy.zaporozhets@gmail.com\",\n" +
                "  \"committer_name\": \"Dmitriy\",\n" +
                "  \"committer_email\": \"dmitriy.zaporozhets@gmail.com\",\n" +
                "  \"created_at\": \"2012-09-20T09:06:12+03:00\",\n" +
                "  \"message\": \"Sanitize for network graph\",\n" +
                "  \"committed_date\": \"2012-09-20T09:06:12+03:00\",\n" +
                "  \"authored_date\": \"2012-09-20T09:06:12+03:00\",\n" +
                "  \"parent_ids\": [\n" +
                "    \"ae1d9fb46aa2b07ee9836d49862ec4e2c46fbbba\"\n" +
                "  ],\n" +
                "  \"stats\": {\n" +
                "    \"additions\": 15,\n" +
                "    \"deletions\": 10,\n" +
                "    \"total\": 25\n" +
                "  },\n" +
                "  \"status\": \"running\"\n" +
                "}"));

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[\n" +
                "  {\n" +
                "    \"id\": 1,\n" +
                "    \"username\": \"john_smith\",\n" +
                "    \"name\": \"John Smith\",\n" +
                "    \"state\": \"active\",\n" +
                "    \"avatar_url\": \"http://localhost:3000/uploads/user/avatar/1/cd8.jpeg\",\n" +
                "    \"web_url\": \"http://localhost:3000/john_smith\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 2,\n" +
                "    \"username\": \"jack_smith\",\n" +
                "    \"name\": \"Jack Smith\",\n" +
                "    \"state\": \"blocked\",\n" +
                "    \"avatar_url\": \"http://gravatar.com/../e32131cd8.jpeg\",\n" +
                "    \"web_url\": \"http://localhost:3000/jack_smith\"\n" +
                "  }\n" +
                "]"));


        GitLabApiV3Wrapper facade = new GitLabApiV3Wrapper(gitLabPluginConfiguration, sonarFacade);
        facade.setGitLabAPI(GitLabAPI.connect(gitLabPluginConfiguration.url(), gitLabPluginConfiguration.userToken()));
        GitLabProject gitLabProject = Mockito.mock(GitLabProject.class);
        Mockito.when(gitLabProject.getId()).thenReturn(1);
        facade.setGitLabProject(gitLabProject);

        Assertions.assertThat(facade.getUsernameForRevision("6104942438c14ec7bd21c6cd5bd995272b3faff6")).isNull();
    }

    @Test
    public void testGetUsernameForRevisionExist() throws Exception {
        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("{\n" +
                "  \"id\": \"6104942438c14ec7bd21c6cd5bd995272b3faff6\",\n" +
                "  \"short_id\": \"6104942438c\",\n" +
                "  \"title\": \"Sanitize for network graph\",\n" +
                "  \"author_name\": \"randx\",\n" +
                "  \"author_email\": \"john@example.com\",\n" +
                "  \"committer_name\": \"Dmitriy\",\n" +
                "  \"committer_email\": \"dmitriy.zaporozhets@gmail.com\",\n" +
                "  \"created_at\": \"2012-09-20T09:06:12+03:00\",\n" +
                "  \"message\": \"Sanitize for network graph\",\n" +
                "  \"committed_date\": \"2012-09-20T09:06:12+03:00\",\n" +
                "  \"authored_date\": \"2012-09-20T09:06:12+03:00\",\n" +
                "  \"parent_ids\": [\n" +
                "    \"ae1d9fb46aa2b07ee9836d49862ec4e2c46fbbba\"\n" +
                "  ],\n" +
                "  \"stats\": {\n" +
                "    \"additions\": 15,\n" +
                "    \"deletions\": 10,\n" +
                "    \"total\": 25\n" +
                "  },\n" +
                "  \"status\": \"running\"\n" +
                "}"));

        gitlab.enqueue(new MockResponse().setResponseCode(200).setBody("[\n" +
                "  {\n" +
                "    \"id\": 1,\n" +
                "    \"username\": \"john_smith\",\n" +
                "    \"email\": \"john@example.com\",\n" +
                "    \"name\": \"John Smith\",\n" +
                "    \"state\": \"active\",\n" +
                "    \"avatar_url\": \"http://localhost:3000/uploads/user/avatar/1/cd8.jpeg\",\n" +
                "    \"web_url\": \"http://localhost:3000/john_smith\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": 2,\n" +
                "    \"username\": \"jack_smith\",\n" +
                "    \"email\": \"jack@example.com\",\n" +
                "    \"name\": \"Jack Smith\",\n" +
                "    \"state\": \"blocked\",\n" +
                "    \"avatar_url\": \"http://gravatar.com/../e32131cd8.jpeg\",\n" +
                "    \"web_url\": \"http://localhost:3000/jack_smith\"\n" +
                "  }\n" +
                "]"));


        GitLabApiV3Wrapper facade = new GitLabApiV3Wrapper(gitLabPluginConfiguration, sonarFacade);
        facade.setGitLabAPI(GitLabAPI.connect(gitLabPluginConfiguration.url(), gitLabPluginConfiguration.userToken()));
        GitLabProject gitLabProject = Mockito.mock(GitLabProject.class);
        Mockito.when(gitLabProject.getId()).thenReturn(1);
        facade.setGitLabProject(gitLabProject);

        Assertions.assertThat(facade.getUsernameForRevision("6104942438c14ec7bd21c6cd5bd995272b3faff6")).isEqualTo("john_smith");
    }
}
