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

import com.talanlabs.sonar.plugins.gitlab.models.QualityGate;

import java.util.List;
import java.util.stream.Stream;

public class QualityGateConditionCountTemplateMethodModelEx extends AbstractQualityGateConditionsTemplateMethodModelEx {

    public QualityGateConditionCountTemplateMethodModelEx(List<QualityGate.Condition> conditions) {
        super(conditions);
    }

    @Override
    protected Object exec(Stream<QualityGate.Condition> stream) {
        return (int)stream.count();
    }
}
