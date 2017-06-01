# Default global template

```injectedfreemarker
<#assign newIssueCount = issueCount() notReportedIssueCount = issueCount(false)>
<#assign hasInlineIssues = newIssueCount gt notReportedIssueCount extraIssuesTruncated = notReportedIssueCount gt maxGlobalIssues>
<#if newIssueCount == 0>
L'analyse de SonarQube indique aucun probl&egrave;me.
<#else>
L'analyse de SonarQube indique ${newIssueCount} probl&egrave;me<#if newIssueCount gt 1>s</#if>
    <#assign newIssuesBlocker = issueCount(BLOCKER) newIssuesCritical = issueCount(CRITICAL) newIssuesMajor = issueCount(MAJOR) newIssuesMinor = issueCount(MINOR) newIssuesInfo = issueCount(INFO)>
    <#if newIssuesBlocker gt 0>
* ${emojiSeverity(BLOCKER)} ${newIssuesBlocker} bloquante<#if newIssuesBlocker gt 1>s</#if>
    </#if>
    <#if newIssuesCritical gt 0>
* ${emojiSeverity(CRITICAL)} ${newIssuesCritical} critique<#if newIssuesCritical gt 1>s</#if>
    </#if>
    <#if newIssuesMajor gt 0>
* ${emojiSeverity(MAJOR)} ${newIssuesMajor} majeur<#if newIssuesMajor gt 1>s</#if>
    </#if>
    <#if newIssuesMinor gt 0>
* ${emojiSeverity(MINOR)} ${newIssuesMinor} mineur<#if newIssuesMinor gt 1>s</#if>
    </#if>
    <#if newIssuesInfo gt 0>
* ${emojiSeverity(INFO)} ${newIssuesInfo} info<#if newIssuesInfo gt 1>s</#if>
    </#if>
    <#if !disableIssuesInline && hasInlineIssues>

Regarder les commentaires dans cette conversation pour les consulter.
    </#if>
    <#if notReportedIssueCount gt 0>
        <#if !disableIssuesInline>
            <#if hasInlineIssues || extraIssuesTruncated>
                <#if notReportedIssueCount <= maxGlobalIssues>

#### ${notReportedIssueCount} probl&egrave;me<#if notReportedIssueCount gt 1>s</#if> en plus
                <#else>

#### Top ${maxGlobalIssues} probl&egrave;me<#if maxGlobalIssues gt 1>s</#if> en plus
                </#if>
            </#if>

Remarque: Les probl&egrave;mes suivants ont &eacute;t&eacute; trouv&eacute;s sur des lignes qui n'ont pas &eacute;t&eacute; modifi&eacute;es dans le commit. Puisque ces probl&egrave;mes ne peuvent pas &ecirc;tre signal&eacute;s dans les commentaires de ligne, ils sont r&eacute;sum&eacute;s ici :
        <#elseif extraIssuesTruncated>

#### Top ${maxGlobalIssues} des probl&egrave;me<#if maxGlobalIssues gt 1>s</#if>
        </#if>

        <#assign reportedIssueCount = 0>
        <#list issues(false) as issue>
            <#if reportedIssueCount < maxGlobalIssues>
1. ${print(issue)}
            </#if>
            <#assign reportedIssueCount++>
        </#list>
        <#if notReportedIssueCount gt maxGlobalIssues>
* ... ${notReportedIssueCount-maxGlobalIssues} en plus
        </#if>
    </#if>
</#if>
```
