# Default global template with image

```injectedfreemarker
<#assign newIssueCount = issueCount() notReportedIssueCount = issueCount(false)>
<#assign hasInlineIssues = newIssueCount gt notReportedIssueCount extraIssuesTruncated = notReportedIssueCount gt maxGlobalIssues>
<#if newIssueCount == 0>
SonarQube analysis reported no issues.
<#else>
SonarQube analysis reported ${newIssueCount} issue<#if newIssueCount gt 1>s</#if>
    <#assign newIssuesBlocker = issueCount(BLOCKER) newIssuesCritical = issueCount(CRITICAL) newIssuesMajor = issueCount(MAJOR) newIssuesMinor = issueCount(MINOR) newIssuesInfo = issueCount(INFO)>
    <#if newIssuesBlocker gt 0>
* ${imageSeverity(BLOCKER)} ${newIssuesBlocker} blocker
    </#if>
    <#if newIssuesCritical gt 0>
* ${imageSeverity(CRITICAL)} ${newIssuesCritical} critical
    </#if>
    <#if newIssuesMajor gt 0>
* ${imageSeverity(MAJOR)} ${newIssuesMajor} major
    </#if>
    <#if newIssuesMinor gt 0>
* ${imageSeverity(MINOR)} ${newIssuesMinor} minor
    </#if>
    <#if newIssuesInfo gt 0>
* ${imageSeverity(INFO)} ${newIssuesInfo} info
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
1. <@p issue=issue/>
            </#if>
            <#assign reportedIssueCount++>
        </#list>
        <#if notReportedIssueCount gt maxGlobalIssues>
* ... ${notReportedIssueCount-maxGlobalIssues} more
        </#if>
    </#if>
</#if>
<#macro p issue>
${imageSeverity(issue.severity)} <#if issue.url??>[${issue.message}](${issue.url})<#else>${issue.message}<#if issue.componentKey??> (${issue.componentKey})</#if></#if> [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](${ruleLink(issue.ruleKey)})
</#macro>
```