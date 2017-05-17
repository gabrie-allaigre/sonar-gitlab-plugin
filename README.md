Sonar GitLab Plugin
===================

[![Build Status](https://travis-ci.org/gabrie-allaigre/sonar-gitlab-plugin.svg?branch=master)](https://travis-ci.org/gabrie-allaigre/sonar-gitlab-plugin)

Inspired by https://github.com/SonarCommunity/sonar-github

**The version 2.0.1 is directly in the SonarQube update center**

**Version 2.0.1**

- Fixbug : NoClassDefFoundError with internal sonar class #26

**Download 2.0.1 version** https://github.com/gabrie-allaigre/sonar-gitlab-plugin/releases/download/2.0.1/sonar-gitlab-plugin-2.0.1.jar

**Version 2.0.0**

- Use emoticon (Thanks Artpej)
- Change fail rule (Thanks Artpej)
- Add comment for no issue (Thanks frol2103)
- Refactored code (Thanks johnou)
- Disable reporting in global comments
- Disable reporting in inline comments
- Add support Proxy
- Ignore certficate if auto-signed 
- Custom global comment (Template)
- Custom inline comment (Template)
- Get multi SHA for comment inline all commits
- Custom comment maybe empty then no comment added

**Update**

- Add test unit
- Add quality project https://sonarqube.com/dashboard?id=com.talanlabs%3Asonar-gitlab-plugin
- GitLab API is in maven central
- Java 8
- Sonarqube >= 5.6
- Remove personal repository

# Goal

Add to each **commit** GitLab in a global commentary on the new anomalies added by this **commit** and add comment lines of modified files.

**Comment commits:**
![Comment commits](doc/sonar_global.jpg)

**Comment line:**
![Comment line](doc/sonar_inline.jpg)

**Add build line:**
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

For SonarQube >= 5.6:

- Download last version https://github.com/gabrie-allaigre/sonar-gitlab-plugin/releases/download/2.0.1/sonar-gitlab-plugin-2.0.1.jar
- Copy file in extensions directory `SONARQUBE_HOME/extensions/plugins`
- Restart SonarQube

**Optional Plugin: [Add Single Sign-On with GitLab in SonarQube](https://github.com/gabrie-allaigre/sonar-auth-gitlab-plugin)**

## Command line

Example:

```shell
mvn --batch-mode verify sonar:sonar -Dsonar.host.url=$SONAR_URL -Dsonar.analysis.mode=preview -Dsonar.gitlab.commit_sha=$CI_BUILD_REF -Dsonar.gitlab.ref_name=$CI_BUILD_REF_NAME -Dsonar.gitlab.project_id=$CI_PROJECT_ID
```

or for comment inline in all commits of branch:

```shell
mvn --batch-mode verify sonar:sonar -Dsonar.host.url=$SONAR_URL -Dsonar.analysis.mode=preview -Dsonar.gitlab.commit_sha=$(git log --pretty=format:%H origin/master..$CI_BUILD_REF | tr '\n' ',') -Dsonar.gitlab.ref_name=$CI_BUILD_REF_NAME -Dsonar.gitlab.project_id=$CI_PROJECT_ID -Dsonar.gitlab.unique_issue_per_inline=true 
```

## GitLab CI

.gitlab-ci.yml sample for Maven project, comment last commit:

```yml
sonarqube_preview:
  script:
    - git checkout origin/master
    - git merge $CI_BUILD_REF --no-commit --no-ff
    - mvn --batch-mode verify sonar:sonar -Dsonar.host.url=$SONAR_URL -Dsonar.analysis.mode=preview -Dsonar.gitlab.project_id=$CI_PROJECT_PATH -Dsonar.gitlab.commit_sha=$CI_BUILD_REF -Dsonar.gitlab.ref_name=$CI_BUILD_REF_NAME
  stage: test
  except:
    - develop
    - master
    - /^hotfix_.*$/
    - /.*-hotfix$/
  tags:
    - java

sonarqube:
  script:
    - mvn --batch-mode verify sonar:sonar -Dsonar.host.url=$SONAR_URL
  stage: test
  only:
    - master
  tags:
    - java
```

| GitLab 8.x name | GitLab 9.x name |
| -------- | ----------- |
| CI_BUILD_REF | CI_COMMIT_SHA |
| CI_BUILD_REF_NAME | CI_COMMIT_REF_NAME |

https://docs.gitlab.com/ce/ci/variables/#9-0-renaming

## Plugins properties

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
| sonar.gitlab.disable_inline_comments | Disable issue reporting as inline comments (default false) | Administration, Variable | >= 2.0.0 |
| sonar.gitlab.only_issue_from_commit_file | Show issue for commit file only (default false) | Variable | >= 2.0.0 |
| sonar.gitlab.only_issue_from_commit_line | Show issue for commit line only (default false) | Variable | >= 2.1.0 |
| sonar.gitlab.build_init_state | State that should be the first when build commit status update is called (default pending) | Administration, Variable | >= 2.0.0 |
| sonar.gitlab.disable_global_comment | Disable global comment, report only inline (default false) | Administration, Variable | >= 2.0.0 |
| sonar.gitlab.failure_notification_mode | Notification is in current build (exit-code) or in commit status (commit-status) (default commit-status) | Administration, Variable | >= 2.0.0 |
| sonar.gitlab.global_template | Template for global comment in commit | Administration, Variable | >= 2.0.0 |
| sonar.gitlab.ping_user | Ping the user who made an issue by @ mentioning (default false) | Administration, Variable | >= 2.0.0 |
| sonar.gitlab.unique_issue_per_inline | Unique issue per inline comment (default false) | Administration, Variable | >= 2.0.0 |
| sonar.gitlab.prefix_directory | Add prefix when create link for GitLab | Variable | >= 2.1.0 |

- Administration : **Settings** globals in SonarQube
- Project : **Settings** of project in SonarQube
- Variable : In an environment variable or in the `pom.xml` either from the command line with` -D`

# Configuration

- In SonarQube: Administration -> General Settings -> GitLab -> **Reporting**. Set GitLab Url and Token

![Sonar settings](doc/sonar_admin_settings.jpg)

- In SonarQube: Project Administration -> General Settings -> GitLab -> **Reporting**. Set project identifier in GitLab

![Sonar settings](doc/sonar_project_settings.jpg)

# Templates

Custom global/inline comment : Change language, change image, change order, print all issues, etc

**Use FreeMarker syntax [http://freemarker.org/](http://freemarker.org/)**

## Variables

Usage : `${name}`

| name | type | description |
| --- | --- | --- |
| url | String | GitLab url |
| projectId | String | Project ID in GitLab or internal id or namespace + name or namespace + path or url http or ssh url or url or web |
| commitSHA | String[] | SHA of the commit comment. Get first `commitSHA[0]` |
| refName | String | Branch name or reference of the commit |
| maxGlobalIssues | Integer | Maximum number of anomalies to be displayed in the global comment |
| maxBlockerIssuesGate | Integer | Max blocker issue for build failed |
| maxCriticalIssuesGate | Integer | Max critical issue for build failed |
| maxMajorIssuesGate | Integer | Max major issue for build failed |
| maxMinorIssuesGate | Integer | Max minor issue for build failed |
| maxInfoIssuesGate | Integer | Max info issue for build failed |
| disableIssuesInline | Boolean | Disable issue reporting as inline comments |
| disableGlobalComment | Boolean | Disable global comment, report only inline |
| onlyIssueFromCommitFile | Boolean | Show issue for commit file only |
| commentNoIssue | Boolean | Add a comment even when there is no new issue |
| revision | String | Current revision |
| author | String | Commit's author for inline |
| lineNumber | Integer | Current line number for inline issues only |
| BLOCKER | Severity | Blocker |
| CRITICAL | Severity | Critical |
| MAJOR | Severity | Major |
| MINOR | Severity | Minor |
| INFO | Severity | Info |

## Functions

Usage : `${name(arg1,arg2,...)}`

| name | arguments | type | description |
| --- | --- | --- | --- |
| issueCount | none | Integer | Get new issue count |
| issueCount | Boolean | Integer | Get new issue count if true only reported else false only not reported |
| issueCount | Severity | Integer | Get new issue count by Severity |
| issueCount | Boolean, Severity | Integer | Get new issue count by Severity if true only reported else false only not reported |
| issues | none | List<Issue> | Get new issues |
| issues | Boolean | List<Issue> | Get new issues if true only reported else false only not reported |
| issues | Severity | List<Issue> | Get new issues by Severity |
| issues | Boolean, Severity | List<Issue> | Get new issues by Severity if true only reported else false only not reported |
| print | Issue | String | Print a issue line (same default template) |
| emojiSeverity | Severity | String | Print a emoji by severity |
| imageSeverity | Severity | String | Print a image by severity |
| ruleLink | String | String | Get URL for rule in SonarQube |

### Type 

Usage : `${Issue.name}`

| name | type | description |
| --- | --- | --- |
| reportedOnDiff | Boolean | Reported inline |
| url | String | URL of file/line in GitLab |
| componentKey | String | Component key |
| severity | Severity | Severity of issue |
| line | Integer | Line (maybe null) |
| key | String | Key |
| message | String | Message (maybe null) |
| ruleKey | String | Rule key on SonarQube |
| new | Boolean | New issue |

## Examples

### Global

```injectedfreemarker
<#assign newIssueCount = issueCount() notReportedIssueCount = issueCount(false)>
<#assign hasInlineIssues = newIssueCount gt notReportedIssueCount extraIssuesTruncated = notReportedIssueCount gt maxGlobalIssues>
<#if newIssueCount == 0>
SonarQube analysis reported no issues.
<#else>
SonarQube analysis reported ${newIssueCount} issue<#if newIssueCount gt 1>s</#if>
    <#assign newIssuesBlocker = issueCount(BLOCKER) newIssuesCritical = issueCount(CRITICAL) newIssuesMajor = issueCount(MAJOR) newIssuesMinor = issueCount(MINOR) newIssuesInfo = issueCount(INFO)>
    <#if newIssuesBlocker gt 0>
* ${emojiSeverity(BLOCKER)} ${newIssuesBlocker} blocker
    </#if>
    <#if newIssuesCritical gt 0>
* ${emojiSeverity(CRITICAL)} ${newIssuesCritical} critical
    </#if>
    <#if newIssuesMajor gt 0>
* ${emojiSeverity(MAJOR)} ${newIssuesMajor} major
    </#if>
    <#if newIssuesMinor gt 0>
* ${emojiSeverity(MINOR)} ${newIssuesMinor} minor
    </#if>
    <#if newIssuesInfo gt 0>
* ${emojiSeverity(INFO)} ${newIssuesInfo} info
    </#if>
    <#if !disableIssuesInline && hasInlineIssues>

Watch the comments in this conversation to review them.
    </#if>
    <#if notReportedIssueCount gt 0>
        <#if !disableIssuesInline>
            <#if hasInlineIssues || extraIssuesTruncated>
                <#if notReportedIssueCount <= maxGlobalIssues>

#### ${notReportedIssueCount} extra issue<#if notReportedIssueCount gt 1>s</#if>
                <#else>

#### Top ${maxGlobalIssues} extra issue<#if maxGlobalIssues gt 1>s</#if>
                </#if>
            </#if>

Note: The following issues were found on lines that were not modified in the commit. Because these issues can't be reported as line comments, they are summarized here:
        <#elseif extraIssuesTruncated>

#### Top ${maxGlobalIssues} issue<#if maxGlobalIssues gt 1>s</#if>
        </#if>

        <#assign reportedIssueCount = 0>
        <#list issues(false) as issue>
            <#if reportedIssueCount < maxGlobalIssues>
1. ${print(issue)}
            </#if>
            <#assign reportedIssueCount++>
        </#list>
        <#if notReportedIssueCount gt maxGlobalIssues>
* ... ${notReportedIssueCount-maxGlobalIssues} more
        </#if>
    </#if>
</#if>
```

**Others examples for global :**
- [Template Default](templates/global/default.md) Current template
- [Template Default with Images](templates/global/default-image.md) Same template as default but with images
- [Template All Issues](templates/global/all-issues.md) Print all issues

### Inline

```injectedfreemarker
<#list issues() as issue>
<@p issue=issue/>
</#list>
<#macro p issue>
${emojiSeverity(issue.severity)} ${issue.message} [:blue_book:](${ruleLink(issue.ruleKey)})
</#macro>
```

**Others examples for inline :**
- [Template Default](templates/inline/default.md) Current template
- [Template Default with Images](templates/inline/default-image.md) Same template as default but with images

# Tips

## Import GitLab SSL certifcate

- **On your server** import Gitlab SSL certificate into the JRE used by SonarQube

If you don't already have you certificate on the SonarQube server, run `openssl s_client -connect mygitlab.com:443 -showcerts > /home/${USER}/mygitlab.crt`

Import it into your JRE cacerts (you can check from the "System Info" page in the Administration section of your sonarqube instance), running `sudo $JDK8/bin/keytool -import -file ~/mygitlab.crt -keystore $JDK8/jre/lib/security/cacerts -alias mygitlab`.

Restart your SonarQube instance.

# Changes

- Add commit only line
- Add prefix workspace (not found .git folder)