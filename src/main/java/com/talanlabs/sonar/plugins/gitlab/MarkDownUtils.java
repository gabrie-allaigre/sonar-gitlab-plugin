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

import org.sonar.api.CoreProperties;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.Settings;

import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
@BatchSide
public class MarkDownUtils {

    private static final String IMAGES_ROOT_URL = "https://github.com/gabrie-allaigre/sonar-gitlab-plugin/raw/master/images/";

    private final String ruleUrlPrefix;

    public MarkDownUtils(Settings settings) {
        // If server base URL was not configured in SQ server then is is better to take URL configured on batch side
        String baseUrl = settings.hasKey(CoreProperties.SERVER_BASE_URL) ? settings.getString(CoreProperties.SERVER_BASE_URL) : settings.getString("sonar.host.url");
        if (baseUrl == null) {
            baseUrl = "http://localhost:9090";
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        this.ruleUrlPrefix = baseUrl;
    }

    private String encodeForUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Encoding not supported", e);
        }
    }

    public String getEmojiForSeverity(Severity severity) {
        switch (severity) {
            case BLOCKER:
                return ":no_entry:";
            case CRITICAL:
                return ":no_entry_sign:";
            case MAJOR:
                return ":warning:";
            case MINOR:
                return ":arrow_down_small:";
            case INFO:
                return ":information_source:";
            default:
                return ":grey_question:";
        }
    }

    public String getImageForSeverity(Severity severity) {
        return "![" + severity + "](" + IMAGES_ROOT_URL + "severity-" + severity.name().toLowerCase() + ".png)";
    }

    public String printIssue(Severity severity, String message, String ruleKey, @Nullable String url, @Nullable String componentKey) {
        StringBuilder sb = new StringBuilder();
        sb.append(getEmojiForSeverity(severity)).append(" ");
        if (url != null) {
            sb.append("[").append(message).append("]").append("(").append(url).append(")");
        } else {
            sb.append(message);
            if (componentKey != null) {
                sb.append(" ").append("(").append(componentKey).append(")");
            }
        }
        sb.append(" ").append("[:blue_book:](").append(getRuleLink(ruleKey)).append(")");
        return sb.toString();
    }

    public String getRuleLink(String ruleKey) {
        return ruleUrlPrefix + "coding_rules#rule_key=" + encodeForUrl(ruleKey);
    }
}
