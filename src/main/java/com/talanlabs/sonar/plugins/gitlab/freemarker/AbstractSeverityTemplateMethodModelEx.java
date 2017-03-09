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

import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateScalarModel;
import org.sonar.api.batch.rule.Severity;

import java.util.List;

public abstract class AbstractSeverityTemplateMethodModelEx implements TemplateMethodModelEx {

    AbstractSeverityTemplateMethodModelEx() {
        super();
    }

    @Override
    public Object exec(List arguments) throws TemplateModelException {
        if (arguments.size() == 1) {
            return execOneArg(arguments.get(0));
        }
        throw new TemplateModelException("Failed call accept 1 Severity arg (INFO,MINOR,MAJOR,CRITICAL,BLOCKER)");
    }

    protected abstract Object exec(Severity severity);

    private Object execOneArg(Object arg) throws TemplateModelException {
        if (arg instanceof TemplateScalarModel) {
            String name = ((TemplateScalarModel) arg).getAsString();
            try {
                Severity severity = Severity.valueOf(name);
                return exec(severity);
            } catch (IllegalArgumentException e) {
                throw new TemplateModelException("Failed call 1 Severity arg (INFO,MINOR,MAJOR,CRITICAL,BLOCKER)", e);
            }
        }
        throw new TemplateModelException("Failed call accept 1 Severity arg (INFO,MINOR,MAJOR,CRITICAL,BLOCKER)");
    }
}
