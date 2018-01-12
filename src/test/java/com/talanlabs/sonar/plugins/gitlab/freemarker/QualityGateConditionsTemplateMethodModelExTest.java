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

import com.talanlabs.sonar.plugins.gitlab.models.QualityGate;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateModelException;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.*;

public class QualityGateConditionsTemplateMethodModelExTest {

    private QualityGateConditionsTemplateMethodModelEx qualityGateConditionsTemplateMethodModelEx;

    private List<Map<String, Object>> list(List<Object> arguments) {
        try {
            return (List<Map<String, Object>>) qualityGateConditionsTemplateMethodModelEx.exec(arguments);
        } catch (TemplateModelException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testSuccessEmpty() {
        qualityGateConditionsTemplateMethodModelEx = new QualityGateConditionsTemplateMethodModelEx(Collections.emptyList());

        Assertions.assertThat(list(Collections.emptyList())).isEmpty();
        Assertions.assertThat(list(Collections.singletonList(new SimpleScalar("OK")))).isEmpty();
    }

    @Test
    public void testSuccess() {
        List<QualityGate.Condition> conditions = new ArrayList<>();
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto1").actual("10").symbol("<").warning("").error("0").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto2").actual("11").symbol(">=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto3").actual("13").symbol("<=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto4").actual("14").symbol(">").warning("20").error("30").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto5").actual("15").symbol("=").warning("10").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.ERROR).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("20").error("40").build());
        qualityGateConditionsTemplateMethodModelEx = new QualityGateConditionsTemplateMethodModelEx(conditions);

        Assertions.assertThat(list(Collections.emptyList())).hasSize(6);
        Assertions.assertThat(list(Collections.singletonList(new SimpleScalar("OK")))).hasSize(3);
    }

    @Test
    public void testFailedEmpty() {
        List<QualityGate.Condition> conditions = new ArrayList<>();
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto1").actual("10").symbol("<").warning("").error("0").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto2").actual("11").symbol(">=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto3").actual("13").symbol("<=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto4").actual("14").symbol(">").warning("20").error("30").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto5").actual("15").symbol("=").warning("10").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.ERROR).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("20").error("40").build());
        qualityGateConditionsTemplateMethodModelEx = new QualityGateConditionsTemplateMethodModelEx(conditions);

        Assertions.assertThatThrownBy(() -> list(Collections.singletonList(null))).hasCauseInstanceOf(TemplateModelException.class);
        Assertions.assertThatThrownBy(() -> list(Collections.singletonList(new SimpleScalar("TOTO")))).hasCauseInstanceOf(TemplateModelException.class);
        Assertions.assertThatThrownBy(() -> list(Arrays.asList(TemplateBooleanModel.FALSE, new SimpleScalar("MAJOR")))).hasCauseInstanceOf(TemplateModelException.class);
    }
}
