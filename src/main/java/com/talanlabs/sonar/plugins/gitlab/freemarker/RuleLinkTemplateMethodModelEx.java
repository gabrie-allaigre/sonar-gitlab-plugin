/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2016-2017 Talanlabs
 * gabriel.allaigre@talanlabs.com
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
package com.talanlabs.sonar.plugins.gitlab.freemarker;

import com.talanlabs.sonar.plugins.gitlab.CommitFacade;
import com.talanlabs.sonar.plugins.gitlab.GitLabPluginConfiguration;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateScalarModel;

import java.util.List;

public class RuleLinkTemplateMethodModelEx implements TemplateMethodModelEx {

    private final String ruleUrlPrefix;

    public RuleLinkTemplateMethodModelEx(GitLabPluginConfiguration gitLabPluginConfiguration) {
        super();

        this.ruleUrlPrefix = gitLabPluginConfiguration.baseUrl();
    }

    @Override
    public Object exec(List arguments) throws TemplateModelException {
        if (arguments.size() == 1) {
            return execOneArg(arguments.get(0));
        }
        throw new TemplateModelException("Failed call accept 1 url arg");
    }

    private Object execOneArg(Object arg) throws TemplateModelException {
        if (arg instanceof TemplateScalarModel) {
            String name = ((TemplateScalarModel) arg).getAsString();
            return ruleUrlPrefix + "coding_rules#rule_key=" + CommitFacade.encodeForUrl(name);
        }
        throw new TemplateModelException("Failed call accept 1 url arg");
    }
}
