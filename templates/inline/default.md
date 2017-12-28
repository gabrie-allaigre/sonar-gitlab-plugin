# Default inline template

```injectedfreemarker
<#list issues() as issue>
<@p issue=issue/>
</#list>
<#macro p issue>
${emojiSeverity(issue.severity)} ${issue.message} [why?](${ruleLink(issue.ruleKey)}) | [Resolve in SonarQube](https://sonar.yourDomain.com/project/issues?id=${issue.componentKey}&issues=${issue.key}&open=${issue.key})
</#macro>
```