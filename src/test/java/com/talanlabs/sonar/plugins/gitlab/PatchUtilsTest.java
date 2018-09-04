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

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class PatchUtilsTest {

    @Test
    public void testEmpty() {
        Assertions.assertThat(PatchUtils.getPositionsFromPatch("12313")).isEmpty();
    }

    @Test
    public void testWrong() {
        Assertions.assertThatThrownBy(() -> PatchUtils.getPositionsFromPatch("@ wrong")).isInstanceOf(IllegalStateException.class).hasMessage("Unable to parse line:\n" +
                "\t@ wrong\n" +
                "Full patch: \n" +
                "\t@ wrong");
    }

    @Test
    public void testCorrect() {
        Assertions.assertThat(PatchUtils.getPositionsFromPatch("@@ -78,6 +78,27 @@\n" +
                "\t\t\t\t\"src/styles.scss\",\n" +
                "                \"src/cordova-styles.scss\"\n" +
                "              ]\n" +
                "            },\n" +
                "+           \"prod-cordova\": {\n" +
                "+             \"optimization\": true,\n" +
                "+             \"outputHashing\": \"all\",\n" +
                "              \"sourceMap\": false,\n" +
                "              \"extractCss\": true,\n" +
                "              \"namedChunks\": false,\n" +
                "              \"aot\": true,\n" +
                "              \"extractLicenses\": true,\n" +
                "              \"vendorChunk\": false,\n" +
                "              \"buildOptimizer\": true,\n" +
                "              \"fileReplacements\": [\n" +
                "                {\n" +
                "                  \"replace\": \"src/environments/environment.ts\",\n" +
                "                  \"with\": \"src/environments/environment.prod-cordova.ts\"\n" +
                "                }\n" +
                "              ],\n" +
                "              \"styles\": [\n" +
                "                \"src/styles.scss\",\n" +
                "                \"src/cordova-styles.scss\"\n" +
                "              ]\n" +
                "            }\n" +
                "          }\n" +
                "        },")).isNotEmpty().hasSize(3).containsExactlyInAnyOrder(
                new IGitLabApiWrapper.Line(83, "             \"outputHashing\": \"all\","),
                new IGitLabApiWrapper.Line(82, "             \"optimization\": true,"),
                new IGitLabApiWrapper.Line(81, "           \"prod-cordova\": {")
        );
    }
}
