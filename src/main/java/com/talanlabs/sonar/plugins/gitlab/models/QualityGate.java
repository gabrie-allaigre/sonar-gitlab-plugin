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
package com.talanlabs.sonar.plugins.gitlab.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QualityGate {

    private Status status;
    private List<Condition> conditions;

    private QualityGate() {
        // Nothing
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public Status getStatus() {
        return status;
    }

    public List<Condition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    public enum Status {

        OK, WARN, ERROR, NONE;

        public static Status of(String name) {
            for (Status m : values()) {
                if (m.name().equals(name)) {
                    return m;
                }
            }
            return null;
        }
    }

    public static class Builder {

        private QualityGate qualityGate;

        private Builder() {
            this.qualityGate = new QualityGate();
        }

        public Builder status(Status status) {
            this.qualityGate.status = status;
            return this;
        }

        public Builder conditions(List<Condition> conditions) {
            this.qualityGate.conditions = new ArrayList<>(conditions);
            return this;
        }

        public QualityGate build() {
            return qualityGate;
        }
    }

    public static class Condition {

        private Status status;
        private String metricKey;
        private String metricName;
        private String actual;
        private String symbol;
        private String warning;
        private String error;

        private Condition() {
            // Nothing
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public Status getStatus() {
            return status;
        }

        public String getMetricKey() {
            return metricKey;
        }

        public String getMetricName() {
            return metricName;
        }

        public String getActual() {
            return actual;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getWarning() {
            return warning;
        }

        public String getError() {
            return error;
        }

        public static class Builder {

            private Condition condition;

            private Builder() {
                this.condition = new Condition();
            }

            public Builder status(Status status) {
                this.condition.status = status;
                return this;
            }

            public Builder metricKey(String metricKey) {
                this.condition.metricKey = metricKey;
                return this;
            }

            public Builder metricName(String metricName) {
                this.condition.metricName = metricName;
                return this;
            }

            public Builder actual(String actual) {
                this.condition.actual = actual;
                return this;
            }

            public Builder symbol(String symbol) {
                this.condition.symbol = symbol;
                return this;
            }

            public Builder warning(String warning) {
                this.condition.warning = warning;
                return this;
            }

            public Builder error(String error) {
                this.condition.error = error;
                return this;
            }

            public Condition build() {
                return condition;
            }
        }
    }
}
