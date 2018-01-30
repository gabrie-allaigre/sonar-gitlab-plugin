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
package com.talanlabs.sonar.plugins.gitlab.models;

public class Rule {

    private String key;
    private String repo;
    private String name;
    private String description;
    private Type type;
    private String debtRemFnType;
    private String debtRemFnBaseEffort;

    private Rule() {
        // Nothing
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public String getKey() {
        return key;
    }

    public String getRepo() {
        return repo;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Type getType() {
        return type;
    }

    public String getDebtRemFnType() {
        return debtRemFnType;
    }

    public String getDebtRemFnBaseEffort() {
        return debtRemFnBaseEffort;
    }

    public enum Type {

        CODE_SMELL,
        BUG,
        VULNERABILITY

    }

    public static class Builder {

        private Rule rule;

        private Builder() {
            this.rule = new Rule();
        }

        public Builder key(String key) {
            this.rule.key = key;
            return this;
        }

        public Builder repo(String repo) {
            this.rule.repo = repo;
            return this;
        }

        public Builder name(String name) {
            this.rule.name = name;
            return this;
        }

        public Builder description(String description) {
            this.rule.description = description;
            return this;
        }

        public Builder type(Type type) {
            this.rule.type = type;
            return this;
        }

        public Builder debtRemFnType(String debtRemFnType) {
            this.rule.debtRemFnType = debtRemFnType;
            return this;
        }

        public Builder debtRemFnBaseEffort(String debtRemFnBaseEffort) {
            this.rule.debtRemFnBaseEffort = debtRemFnBaseEffort;
            return this;
        }

        public Rule build() {
            return rule;
        }
    }
}
