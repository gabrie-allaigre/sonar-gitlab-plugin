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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.Files;
import com.talanlabs.sonar.plugins.gitlab.models.Issue;
import com.talanlabs.sonar.plugins.gitlab.models.QualityGate;
import com.talanlabs.sonar.plugins.gitlab.models.Rule;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.Configuration;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Metric;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.scanner.ScannerSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Ce;
import org.sonarqube.ws.Issues;
import org.sonarqube.ws.MediaTypes;
import org.sonarqube.ws.Qualitygates;
import org.sonarqube.ws.Rules;
import org.sonarqube.ws.client.GetRequest;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.HttpException;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.WsResponse;
import org.sonarqube.ws.client.ce.TaskRequest;
import org.sonarqube.ws.client.components.ShowRequest;
import org.sonarqube.ws.client.issues.SearchRequest;
import org.sonarqube.ws.client.qualitygates.ProjectStatusRequest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Facade for all WS interaction with Sonar
 */
@ScannerSide
public class SonarFacade {

    private static final Logger LOG = Loggers.get(SonarFacade.class);
    private static final String LOG_MSG = "{}: {} {} {}";
    private static final int MAX_SEARCH_ISSUES = 10000;
    private final GitLabPluginConfiguration gitLabPluginConfiguration;
    private final WsClient wsClient;
    private File projectBaseDir;
    private File workDir;

    private Cache<String, File> componentCache = CacheBuilder.newBuilder().build();
    private Cache<String, Rule> ruleCache = CacheBuilder.newBuilder().build();

    public SonarFacade(Configuration settings, GitLabPluginConfiguration gitLabPluginConfiguration) {
        this.gitLabPluginConfiguration = gitLabPluginConfiguration;

        HttpConnector httpConnector = HttpConnector.newBuilder().url(gitLabPluginConfiguration.baseUrl())
                .credentials(settings.get(CoreProperties.LOGIN).orElse(null), settings.get(CoreProperties.PASSWORD).orElse(null)).build();

        wsClient = WsClientFactories.getDefault().newClient(httpConnector);
    }

    public void init(File projectBaseDir, File workDir) {
        this.projectBaseDir = projectBaseDir;
        this.workDir = workDir;
    }

    /**
     * Load quality gate
     *
     * @return current quality gate
     */
    public QualityGate loadQualityGate() {
        Properties reportTaskProps = readReportTaskProperties();

        String analysisId = getAnalysisId(reportTaskProps.getProperty("ceTaskId"));

        Qualitygates.ProjectStatusResponse.ProjectStatus projectStatus = checkQualityGate(analysisId);
        logQualityGate(projectStatus);

        return toQualityGate(projectStatus);
    }

    private QualityGate toQualityGate(Qualitygates.ProjectStatusResponse.ProjectStatus projectStatus) {
        return QualityGate.newBuilder().status(projectStatus.getStatus() != null ? QualityGate.Status.of(projectStatus.getStatus().name()) : null).conditions(
                projectStatus.getConditionsList() != null ? projectStatus.getConditionsList().stream().map(this::toCondition).collect(Collectors.toList()) : Collections.emptyList()).build();
    }

    private QualityGate.Condition toCondition(Qualitygates.ProjectStatusResponse.Condition condition) {
        return QualityGate.Condition.newBuilder().status(condition.getStatus() != null ? QualityGate.Status.of(condition.getStatus().name()) : null).metricKey(condition.getMetricKey()).metricName(
                getMetricName(condition.getMetricKey())).actual(condition.getActualValue()).symbol(getComparatorSymbol(condition.getComparator())).warning(condition.getWarningThreshold()).error(condition.getErrorThreshold()).build();
    }

    private Properties readReportTaskProperties() {
        File reportTaskFile = new File(workDir, "report-task.txt");

        Properties properties = new Properties();
        try {
            properties.load(Files.newReader(reportTaskFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load properties from file " + reportTaskFile, e);
        }
        return properties;
    }

    private String getAnalysisId(String ceTaskId) {
        int queryMaxRetry = gitLabPluginConfiguration.queryMaxRetry();
        long queryWait = gitLabPluginConfiguration.queryWait();

        int retry = 0;
        String analysisId = null;
        do {
            Ce.Task task = getTask(ceTaskId);
            Ce.TaskStatus taskStatus = task.getStatus();

            if (Ce.TaskStatus.SUCCESS.equals(taskStatus)) {
                analysisId = task.getAnalysisId();
            } else if (Ce.TaskStatus.IN_PROGRESS.equals(taskStatus) || Ce.TaskStatus.PENDING.equals(taskStatus)) {
                LOG.info("Waiting quality gate to complete...");
                try {
                    Thread.sleep(queryWait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e.getMessage(), e);
                }
            } else {
                throw new IllegalStateException("Analyze in SonarQube is not success (" + taskStatus + ")");
            }

            retry++;
        } while (retry < queryMaxRetry && analysisId == null);

        if (analysisId == null) {
            LOG.error("Not find analyseId. Try increasing sonar.gitlab.query_max_retry {}, sonar.gitlab.query_wait {}, or both.", gitLabPluginConfiguration.queryMaxRetry(),
                    gitLabPluginConfiguration.queryWait());

            throw new IllegalStateException("Report processing is taking longer than the configured wait limit.");
        }
        return analysisId;
    }

    private Ce.Task getTask(String ceTaskId) {
        Ce.TaskResponse taskResponse = wsClient.ce().task(new TaskRequest().setId(ceTaskId));
        return taskResponse.getTask();
    }

    private Qualitygates.ProjectStatusResponse.ProjectStatus checkQualityGate(String analysisId) {
        LOG.debug("Requesting quality gate status for analysisId {}", analysisId);
        Qualitygates.ProjectStatusResponse projectStatusResponse = wsClient.qualitygates().projectStatus(new ProjectStatusRequest().setAnalysisId(analysisId));
        return projectStatusResponse.getProjectStatus();
    }

    private void logQualityGate(Qualitygates.ProjectStatusResponse.ProjectStatus projectStatus) {
        Qualitygates.ProjectStatusResponse.Status status = projectStatus.getStatus();
        LOG.info("Quality gate status: {}", status);

        logConditions(projectStatus.getConditionsList());
    }

    private void logConditions(List<Qualitygates.ProjectStatusResponse.Condition> conditions) {
        conditions.forEach(this::logCondition);
    }

    private void logCondition(Qualitygates.ProjectStatusResponse.Condition condition) {
        if (Qualitygates.ProjectStatusResponse.Status.OK.equals(condition.getStatus())) {
            LOG.info("{} : {}", getMetricName(condition.getMetricKey()), condition.getActualValue());
        } else if (Qualitygates.ProjectStatusResponse.Status.WARN.equals(condition.getStatus())) {
            LOG.warn(LOG_MSG, getMetricName(condition.getMetricKey()), condition.getActualValue(), getComparatorSymbol(condition.getComparator()), condition.getWarningThreshold());
        } else if (Qualitygates.ProjectStatusResponse.Status.ERROR.equals(condition.getStatus())) {
            LOG.error(LOG_MSG, getMetricName(condition.getMetricKey()), condition.getActualValue(), getComparatorSymbol(condition.getComparator()), condition.getErrorThreshold());
        }
    }

    @VisibleForTesting
    String getMetricName(String metricKey) {
        try {
            Metric metric = CoreMetrics.getMetric(metricKey);
            return metric.getName();
        } catch (NoSuchElementException e) {
            LOG.trace("Using key as name for custom metric '{}' due to '{}'", metricKey, e);
        }
        return metricKey;
    }

    private String getComparatorSymbol(Qualitygates.ProjectStatusResponse.Comparator comparator) {
        if (comparator == null) {
            return "";
        }
        switch (comparator) {
            case GT:
                return ">";
            case LT:
                return "<";
            case EQ:
                return "=";
            case NE:
                return "!=";
            default:
                return comparator.toString();
        }
    }

    public List<Issue> getNewIssues() {
        Properties reportTaskProps = readReportTaskProperties();

        String projectKey = reportTaskProps.getProperty("projectKey");
        String refName = gitLabPluginConfiguration.refName();
        int page = 1;
        Integer nbPage = null;

        List<Issue> issues = new ArrayList<>();
        while (nbPage == null || page <= nbPage) {
            Issues.SearchWsResponse searchWsResponse = searchIssues(projectKey, refName, page);
            nbPage = computeNbPage(searchWsResponse.getTotal(), searchWsResponse.getPs());
            issues.addAll(toIssues(searchWsResponse, refName));

            page++;
        }
        return issues;
    }

    private Issues.SearchWsResponse searchIssues(String componentKey, String branch, int page) {
        SearchRequest searchRequest = new SearchRequest().setComponentKeys(Collections.singletonList(componentKey)).setP(String.valueOf(page)).setResolved("false");
        if (isNotBlankAndNotEmpty(branch)) {
            searchRequest.setBranch(branch);
        }
        return wsClient.issues().search(searchRequest);
    }

    private boolean isNotBlankAndNotEmpty(String branch) {
        return branch != null && !branch.trim().isEmpty();
    }

    private String toString(GetRequest getRequest) {
        String params = getRequest.getParams().entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
        return getRequest.getPath() + "?" + params;
    }

    private int computeNbPage(long total, int pageSize) {
        int maxPage = (int) (MAX_SEARCH_ISSUES / (long) (pageSize + 1)) + 1;
        int nbPage = (int) (total / (long) (pageSize + 1)) + 1;
        return Math.min(nbPage, maxPage);
    }

    private List<Issue> toIssues(Issues.SearchWsResponse issuesSearchWsResponse, String branch) {
        List<Issues.Issue> issues = issuesSearchWsResponse.getIssuesList();
        if (issues == null) {
            return Collections.emptyList();
        }

        List<Issues.Component> components = issuesSearchWsResponse.getComponentsList();

        Predicate<String> supported = ((Predicate<String>) Qualifiers.FILE::equals).or(Qualifiers.UNIT_TEST_FILE::equals);

        List<Issue> res = new ArrayList<>();
        for (Issues.Issue issue : issues) {
            Optional<Issues.Component> componentOptional = components.stream()
                    .filter(c -> supported.test(c.getQualifier()))
                    .filter(c -> c.getKey().equals(issue.getComponent()))
                    .findFirst();

            File file = null;
            if (componentOptional.isPresent()) {
                Issues.Component component = componentOptional.get();
                try {
                    file = componentCache.get(component.getKey(), () -> toFile(componentOptional.get(), branch));
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to get component file for " + component.getKey(), e);
                }
            }
            res.add(toIssue(issue, file));
        }
        return res;
    }

    private File toFile(Issues.Component component, String branch) {
        ShowRequest showRequest = new ShowRequest().setComponent(component.getKey());
        if (isNotBlankAndNotEmpty(branch)) {
            showRequest.setBranch(branch);
        }

        return new File(component.getPath());
    }

    private Issue toIssue(Issues.Issue issue, File relativeFile) {
        File file = relativeFile != null ? new File(projectBaseDir, relativeFile.getPath()) : null;
        return Issue.newBuilder().key(issue.getKey()).ruleKey(issue.getRule()).componentKey(issue.getComponent()).file(file).line(issue.hasLine() ? issue.getLine() : null).message(issue.getMessage())
                .severity(toSeverity(issue.getSeverity())).newIssue(true).build();
    }

    private Severity toSeverity(org.sonarqube.ws.Common.Severity severity) {
        return Severity.valueOf(severity.name());
    }

    public Rule getRule(String ruleKey) {
        try {
            return ruleCache.get(ruleKey, () -> {
                Rules.ShowResponse showResponse = showRule(ruleKey);
                return toRule(showResponse);
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get rule " + ruleKey, e);
        }
    }

    private Rules.ShowResponse showRule(String ruleKey) {
        GetRequest getRequest = new GetRequest("api/rules/show").setParam("key", ruleKey).setMediaType(MediaTypes.PROTOBUF);

        WsResponse wsResponse = wsClient.wsConnector().call(getRequest);

        if (wsResponse.code() != 200) {
            throw new HttpException(wsClient.wsConnector().baseUrl() + toString(getRequest), wsResponse.code(), wsResponse.content());
        }

        try {
            return Rules.ShowResponse.parseFrom(wsResponse.contentStream());
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private Rule toRule(Rules.ShowResponse showResponse) {
        Rules.Rule rule = showResponse.getRule();
        if (rule == null) {
            return Rule.newBuilder().build();
        }
        return Rule.newBuilder()
                .key(rule.getKey())
                .repo(rule.getRepo())
                .name(rule.getName())
                .description(rule.getMdDesc())
                .type(rule.getType() != null ? rule.getType().name() : null)
                .debtRemFnType(rule.getDebtRemFnType())
                .debtRemFnBaseEffort(rule.getRemFnBaseEffort())
                .build();
    }
}
