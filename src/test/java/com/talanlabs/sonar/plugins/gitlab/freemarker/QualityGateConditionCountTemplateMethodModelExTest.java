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
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateModelException;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class QualityGateConditionCountTemplateMethodModelExTest {

    private QualityGateConditionCountTemplateMethodModelEx qualityGateConditionCountTemplateMethodModelEx;

    private Object count(List<Object> arguments) {
        try {
            return qualityGateConditionCountTemplateMethodModelEx.exec(arguments);
        } catch (TemplateModelException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testSuccessEmpty() {
        qualityGateConditionCountTemplateMethodModelEx = new QualityGateConditionCountTemplateMethodModelEx(Collections.emptyList());

        Assertions.assertThat(count(Collections.emptyList())).isEqualTo(0);
        Assertions.assertThat(count(Collections.singletonList(new SimpleScalar("OK")))).isEqualTo(0);
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

        qualityGateConditionCountTemplateMethodModelEx = new QualityGateConditionCountTemplateMethodModelEx(conditions);

        Assertions.assertThat(count(Collections.emptyList())).isEqualTo(6);
        Assertions.assertThat(count(Collections.singletonList(new SimpleScalar("OK")))).isEqualTo(3);
        Assertions.assertThat(count(Collections.singletonList(new SimpleScalar("OK")))).isEqualTo(3);
        Assertions.assertThat(count(Collections.singletonList(new SimpleScalar("OK")))).isEqualTo(3);
    }

    @Test
    public void testFailed() {
        List<QualityGate.Condition> conditions = new ArrayList<>();
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto1").actual("10").symbol("<").warning("").error("0").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto2").actual("11").symbol(">=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.OK).metricKey("toto").metricName("Toto3").actual("13").symbol("<=").warning("").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto4").actual("14").symbol(">").warning("20").error("30").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.WARN).metricKey("toto").metricName("Toto5").actual("15").symbol("=").warning("10").error("").build());
        conditions.add(QualityGate.Condition.newBuilder().status(QualityGate.Status.ERROR).metricKey("toto").metricName("Toto").actual("10").symbol("<").warning("20").error("40").build());

        qualityGateConditionCountTemplateMethodModelEx = new QualityGateConditionCountTemplateMethodModelEx(conditions);

        Assertions.assertThatThrownBy(() -> count(Collections.singletonList(null))).hasCauseInstanceOf(TemplateModelException.class);
        Assertions.assertThatThrownBy(() -> count(Collections.singletonList(new SimpleScalar("TOTO")))).hasCauseInstanceOf(TemplateModelException.class);
        Assertions.assertThatThrownBy(() -> count(Arrays.asList(TemplateBooleanModel.FALSE, new SimpleScalar("MAJOR")))).hasCauseInstanceOf(TemplateModelException.class);
    }
}
