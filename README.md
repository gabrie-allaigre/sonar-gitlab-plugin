Sonar GitLab Plugin
===================

Inspire by https://github.com/SonarCommunity/sonar-github

# Goal

Add to each **commit** GitLab in a global commentary on the new anomalies added by this **commit** and add comment lines of modified files.

Comment commits:
![Comment commits](doc/comment_commits.jpg)

Comment line:
![Comment line](doc/comment_line.jpg)

Add build line:
![Add buids](doc/builds.jpg)

# Usage

For SonarQube < 5.4:

- Download last version https://github.com/gabrie-allaigre/sonar-gitlab-plugin/releases/download/1.6.6/sonar-gitlab-plugin-1.6.6.jar
- Copy file in extensions directory `SONARQUBE_HOME/extensions/plugins`
- Restart SonarQube 

For SonarQube >= 5.4:

- Download last version https://github.com/gabrie-allaigre/sonar-gitlab-plugin/releases/download/1.7.0/sonar-gitlab-plugin-1.7.0.jar
- Copy file in extensions directory `SONARQUBE_HOME/extensions/plugins`
- Restart SonarQube

**Other Plugin: [Add Single Sign-On with GitLab in SonarQube](https://gitlab.talanlabs.com/gabriel-allaigre/sonar-auth-gitlab-plugin)**

# Command line

Example :

``` shell
mvn --batch-mode verify sonar:sonar -Dsonar.host.url=$SONAR_URL -Dsonar.analysis.mode=preview -Dsonar.issuesReport.console.enable=true -Dsonar.gitlab.commit_sha=$CI_BUILD_REF -Dsonar.gitlab.ref_name=$CI_BUILD_REF_NAME
```

| Variable | Comment | Type | Version |
| -------- | ----------- | ---- | --- |
| sonar.gitlab.url | GitLab url | Administration, Variable | >= 1.6.6 |
| sonar.gitlab.max_global_issues | Maximum number of anomalies to be displayed in the global comment |  Administration, Variable | >= 1.6.6 |
| sonar.gitlab.user_token | Token of the user who can make reports on the project, either global or per project |  Administration, Project, Variable | >= 1.6.6 |
| sonar.gitlab.project_id | Project ID in GitLab or internal id or namespace + name or namespace + path or url http or ssh url or url or web | Project, Variable | >= 1.6.6 |
| sonar.gitlab.commit_sha | SHA of the commit comment | Variable | >= 1.6.6 |
| sonar.gitlab.ref_name | Branch name or reference of the commit | Variable | >= 1.6.6 |
| sonar.gitlab.max_blocker_issues_gate | Max blocker issue for build failed (default 0) | Project, Variable | >= 2.0.0 |
| sonar.gitlab.max_critical_issues_gate | Max critical issues for build failed (default 0) | Project, Variable | >= 2.0.0 |
| sonar.gitlab.max_major_issues_gate | Max major issues for build failed (default -1 no fail) | Project, Variable | >= 2.0.0 |
| sonar.gitlab.max_minor_issues_gate | Max minor issues for build failed (default -1 no fail) | Project, Variable | >= 2.0.0 |
| sonar.gitlab.max_info_issues_gate | Max info issues for build failed (default -1 no fail) | Project, Variable | >= 2.0.0 |
| sonar.gitlab.ignore_certificate | Ignore Certificate for access GitLab, use for auto-signing cert (default false) | Administration, Variable | >= 2.0.0 |
| sonar.gitlab.comment_no_issue | Add a comment even when there is no new issue (default false) | Administration, Variable | >= 2.0.0 |
| sonar.gitlab.disableInlineComments | Disable issue reporting as inline comments (default false) | Administration, Variable | >= 2.0.0 |
| sonar.gitlab.onlyIssueFromCommitFile | Show issue for commit file only (default false) | Variable | >= 2.0.0 |

- Administration : **Settings** globals in SonarQube
- Project : **Settings** of project in SonarQube
- Variable : In an environment variable or in the `pom.xml` either from the command line with` -D`

# Configuration

- In SonarQube: Administration -> General Settings -> GitLab -> **Reporting**. Set GitLab Url and Token

![Sonar settings](doc/sonar_settings.jpg)

- In SonarQube: Project Administration -> General Settings -> GitLab -> **Reporting**. Set project identifier in GitLab

![Sonar settings](doc/sonar_project_settings.jpg)

# New version 2.0.0

A new version is work in progress

- Add test unit
- GitLab API is in maven central
- Java 8
- Sonarqube >= 5.6
- New functionnality
- Remove personal repository
- Use emoticon (Thanks Artpej)
- Change fail rule (Thanks Artpej)
- Add comment for no issue (Thanks frol2103)
- Clean code and dead code (Thanks johnou)
- Disable reporting in inline comments
- Add support Proxy
- Ignore certficate if auto-signed
- Add quality project https://sonarqube.com/dashboard?id=com.talanlabs%3Asonar-gitlab-plugin 

