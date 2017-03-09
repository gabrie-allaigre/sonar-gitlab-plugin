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

import com.talanlabs.sonar.plugins.gitlab.GitLabPlugin;
import com.talanlabs.sonar.plugins.gitlab.MarkDownUtils;
import freemarker.template.DefaultMapAdapter;
import freemarker.template.TemplateModelException;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrintTemplateMethodModelExTest {

    private PrintTemplateMethodModelEx printTemplateMethodModelEx;

    @Before
    public void setUp() throws Exception {
        Settings settings = new Settings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL")
                .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE).build()).addComponents(GitLabPlugin.definitions()));

        settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");

        MarkDownUtils markDownUtils = new MarkDownUtils(settings);

        printTemplateMethodModelEx = new PrintTemplateMethodModelEx(markDownUtils);
    }

    private String print(List<Object> arguments) {
        try {
            return (String) printTemplateMethodModelEx.exec(arguments);
        } catch (TemplateModelException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testSuccess() {
        Map<String, Object> root = new HashMap<>();
        root.put("url", "toto");
        root.put("componentKey", "tata");
        root.put("severity", Severity.BLOCKER);
        root.put("message", "titi");
        root.put("ruleKey", "ici");
        Assertions.assertThat(print(Collections.singletonList(DefaultMapAdapter.adapt(root, null)))).isEqualTo(":no_entry: [titi](toto) [:blue_book:](http://myserver/coding_rules#rule_key=ici)");
    }

    @Test
    public void testFailed() {
        Assertions.assertThatThrownBy(() -> print(Collections.emptyList())).hasCauseInstanceOf(TemplateModelException.class);
        Assertions.assertThatThrownBy(() -> print(Collections.singletonList(null))).hasCauseInstanceOf(TemplateModelException.class);
    }
}
