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
package com.talanlabs.sonar.plugins.gitlab;

import org.mockito.Mockito;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.rule.RuleKey;

import javax.annotation.CheckForNull;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

public class Utils {

    private Utils() {
        super();
    }

    public static PostJobIssue newMockedIssue(String componentKey, Severity severity, boolean isNew, String message) {
        return newMockedIssue(componentKey, null, null, severity, isNew, message);
    }

    public static PostJobIssue newMockedIssue(String componentKey, @CheckForNull DefaultInputFile inputFile, @CheckForNull Integer line, Severity severity, boolean isNew, String message) {
        return newMockedIssue(componentKey, inputFile, line, severity, isNew, message, "rule");
    }

    public static PostJobIssue newMockedIssue(String componentKey, @CheckForNull DefaultInputFile inputFile, @CheckForNull Integer line, Severity severity, boolean isNew, String message, String rule) {
        PostJobIssue issue = Mockito.mock(PostJobIssue.class);
        Mockito.when(issue.inputComponent()).thenReturn(inputFile);
        Mockito.when(issue.componentKey()).thenReturn(componentKey);
        if (line != null) {
            Mockito.when(issue.line()).thenReturn(line);
        }
        Mockito.when(issue.ruleKey()).thenReturn(RuleKey.of("repo", rule));
        Mockito.when(issue.severity()).thenReturn(severity);
        Mockito.when(issue.isNew()).thenReturn(isNew);
        Mockito.when(issue.message()).thenReturn(message);

        return issue;
    }

    public static void createFile(File root, String path, String filename, String value) throws IOException {
        File dir = new File(root, path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create directories " + dir.toString());
            }
        }
        File file = new File(dir, filename);
        if (!file.createNewFile()) {
            throw new IOException("Failed to create file " + file.toString());
        }
        try (BufferedWriter bw = Files.newBufferedWriter(file.toPath(), Charset.defaultCharset())) {
            bw.write(value);
        }
    }
}
