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
package com.talanlabs.sonar.plugins.gitlab.freemarker;

import com.talanlabs.sonar.plugins.gitlab.MarkDownUtils;
import freemarker.ext.util.WrapperTemplateModel;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import org.sonar.api.batch.rule.Severity;

import java.util.List;
import java.util.Map;

public class PrintTemplateMethodModelEx implements TemplateMethodModelEx {

    private final MarkDownUtils markDownUtils;

    public PrintTemplateMethodModelEx(MarkDownUtils markDownUtils) {
        super();

        this.markDownUtils = markDownUtils;
    }

    @Override
    public Object exec(List arguments) throws TemplateModelException {
        if (arguments.size() == 1) {
            return execOneArg(arguments.get(0));
        }
        throw new TemplateModelException("Failed call accept 1 issue arg");
    }

    private Object execOneArg(Object arg) throws TemplateModelException {
        if (arg instanceof WrapperTemplateModel && ((WrapperTemplateModel) arg).getWrappedObject() instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) ((WrapperTemplateModel) arg).getWrappedObject();
            return markDownUtils.printIssue((Severity) (map.get("severity")), (String) map.get("message"), (String) map.get("ruleLink"), (String) map.get("url"), (String) map.get("componentKey"));
        }
        throw new TemplateModelException("Failed call accept 1 issue arg");
    }
}
