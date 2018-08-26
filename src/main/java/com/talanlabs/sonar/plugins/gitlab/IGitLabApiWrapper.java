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
package com.talanlabs.sonar.plugins.gitlab;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Objects;

public interface IGitLabApiWrapper {

    void init();

    String getUsernameForRevision(String revision);

    void createOrUpdateSonarQubeStatus(String status, String statusDescription);

    boolean hasFile(String path);

    String getRevisionForLine(File file, String path, int lineNumber);

    boolean hasSameCommitCommentsForFile(String revision, String path, Integer lineNumber, String body);

    @CheckForNull
    String getGitLabUrl(@Nullable String revision, String path, @Nullable Integer issueLine);

    void createOrUpdateReviewComment(String revision, String fullPath, Integer line, String body);

    void addGlobalComment(String comment);

    class Line {

        private Integer number;

        private String content;

        Line(Integer number, String content) {
            this.number = number;
            this.content = content;
        }

        @Override
        public String toString() {
            return "Line{" + "number=" + number +
                    ", content='" + content + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Line line = (Line) o;
            return Objects.equals(number, line.number) &&
                    Objects.equals(content, line.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(number, content);
        }
    }
}
