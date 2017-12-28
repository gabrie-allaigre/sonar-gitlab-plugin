# Default inline template

```injectedfreemarker
<#list issues() as issue>
<@p issue=issue/>
</#list>
<#macro p issue>
${imageSeverity(issue.severity)} ${issue.message} [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](${ruleLink(issue.ruleKey)}) | [View in SonarQube](https://sonar.yourDomain.com/project/issues?id=${issue.componentKey}&issues=${issue.key}&open=${issue.key})
</#macro>
```