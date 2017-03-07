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

import com.talanlabs.sonar.plugins.gitlab.Reporter;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateModelException;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

public class IssuesTemplateMethodModelExTest {

    private IssuesTemplateMethodModelEx issuesTemplateMethodModelEx;

    @Before
    public void setUp() throws Exception {
        List<Reporter.ReportIssue> reportIssues = new ArrayList<>();
        issuesTemplateMethodModelEx = new IssuesTemplateMethodModelEx(reportIssues);
    }

    private List<Map<String, Object>> list(List<Object> arguments) {
        try {
            return (List<Map<String, Object>>) issuesTemplateMethodModelEx.exec(arguments);
        } catch (TemplateModelException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testSuccess() {
        Assertions.assertThat(list(Collections.emptyList())).isEmpty();
        Assertions.assertThat(list(Collections.singletonList(new SimpleScalar("MAJOR")))).isEmpty();
        Assertions.assertThat(list(Collections.singletonList(TemplateBooleanModel.FALSE))).isEmpty();
        Assertions.assertThat(list(Arrays.asList(new SimpleScalar("MAJOR"), TemplateBooleanModel.FALSE))).isEmpty();
    }

    @Test
    public void testFailed() {
        Assertions.assertThatThrownBy(() -> list(Collections.singletonList(null))).hasCauseInstanceOf(TemplateModelException.class);
        Assertions.assertThatThrownBy(() -> list(Collections.singletonList(new SimpleScalar("TOTO")))).hasCauseInstanceOf(TemplateModelException.class);
        Assertions.assertThatThrownBy(() -> list(Arrays.asList(TemplateBooleanModel.FALSE, new SimpleScalar("MAJOR")))).hasCauseInstanceOf(TemplateModelException.class);
        Assertions.assertThatThrownBy(() -> list(Arrays.asList(new SimpleScalar("TOTO"), TemplateBooleanModel.FALSE))).hasCauseInstanceOf(TemplateModelException.class);
        Assertions.assertThatThrownBy(() -> list(Arrays.asList(new SimpleScalar("TOTO"), TemplateBooleanModel.FALSE, TemplateBooleanModel.FALSE))).hasCauseInstanceOf(TemplateModelException.class);
    }
}
