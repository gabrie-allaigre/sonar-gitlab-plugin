# Default inline template

```injectedfreemarker
<#list issues() as issue>
<@p issue=issue/>
</#list>
<#macro p issue>
${imageSeverity(issue.severity)} ${issue.message} [![RULE](https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/rule.png)](${issue.ruleLink})
</#macro>
```