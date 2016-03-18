Sonar GitLab Plugin
===================

Forked from https://github.com/SonarCommunity/sonar-github

# Goal

Add to each **commit** GitLab in a global commentary on the new anomalies added by this **commit** and add comment lines of modified files.

Comment commits:
![Comment commits](doc/comment_commits.jpg)

Comment line:
![Comment line](doc/comment_line.jpg)

Add build line:
![Add buids](doc/builds.jpg)

# Usage

For SonarQube <5.4:

- Download last version http://nexus.talanlabs.com/service/local/repo_groups/public_release/content/com/synaptix/sonar-gitlab-plugin/1.6.6/sonar-gitlab-plugin-1.6.6.jar
- Copy file in extensions directory `SONARQUBE_HOME/extensions/plugins`
- Restart SonarQube 

For SonarQube >=5.4:

- Download last version http://nexus.talanlabs.com/service/local/repo_groups/public_release/content/com/synaptix/sonar-gitlab-plugin/1.7.0/sonar-gitlab-plugin-1.7.0.jar
- Copy file in extensions directory `SONARQUBE_HOME/extensions/plugins`
- Restart SonarQube

**Other Plugin: [Add Single Sign-On with GitLab in SonarQube](https://gitlab.talanlabs.com/gabriel-allaigre/sonar-auth-gitlab-plugin)**

# Command line

Example :

``` shell
mvn --batch-mode verify sonar:sonar -Dsonar.host.url=$SONAR_URL -Dsonar.analysis.mode=preview -Dsonar.issuesReport.console.enable=true -Dsonar.gitlab.commit_sha=$CI_BUILD_REF -Dsonar.gitlab.ref=CI_BUILD_REF_NAME
```

| Variable | Comment | Type |
| -------- | ----------- | ---- |
| sonar.gitlab.url | GitLab url | Administration, Variable |
| sonar.gitlab.max_global_issues | Maximum number of anomalies to be displayed in the global comment |  Administration, Variable |
| sonar.gitlab.user_token | Token of the user who can make reports on the project, either global or per project |  Administration, Project, Variable |
| sonar.gitlab.project_id | Project ID in GitLab or internal id or namespace + name or namespace + path or url http or ssh url or url or web | Project, Variable |
| sonar.gitlab.commit_sha | SHA of the commit comment | Variable |
| sonar.gitlab.ref_name | Branch name or reference of the commit | Variable |

- Administration : **Settings** globals in SonarQube
- Project : **Settings** of project in SonarQube
- Variable : In an environment variable or in the `pom.xml` either from the command line with` -D`

# Configuration

- In SonarQube: Administration -> General Settings -> GitLab -> **Reporting**. Set GitLab Url and Token

![Sonar settings](doc/sonar_settings.jpg)

- In SonarQube: Project Administration -> General Settings -> GitLab -> **Reporting**. Set project identifier in GitLab

![Sonar settings](doc/sonar_project_settings.jpg)
