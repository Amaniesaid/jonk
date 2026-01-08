package com.imt.demo.sonarqube;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class QualityGateEvaluator {

    private final SonarApiClient apiClient;
    private final SonarQubeProperties properties;

    public QualityGateEvaluator(SonarApiClient apiClient, SonarQubeProperties properties) {
        this.apiClient = apiClient;
        this.properties = properties;
    }

    public QualityGateResult evaluateFromReportTask(File reportTaskFile, Consumer<String> logLineConsumer) {
        Map<String, String> report = readReportTask(reportTaskFile);

        String ceTaskId = report.get("ceTaskId");
        if (ceTaskId == null || ceTaskId.isBlank()) {
            throw new IllegalStateException("Missing ceTaskId in report-task.txt");
        }

        emit(logLineConsumer, "Polling SonarQube CE task: " + ceTaskId);

        Instant deadline = Instant.now().plus(properties.getQualityGateTimeout());
        Duration sleep = properties.getPollInterval();

        JsonNode ceTask;
        while (true) {
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("Timeout waiting for SonarQube analysis result (ceTaskId=" + ceTaskId + ")");
            }

            JsonNode json = apiClient.getJson("/api/ce/task", Map.of("id", ceTaskId));
            ceTask = json.get("task");
            if (ceTask == null) {
                throw new IllegalStateException("Unexpected SonarQube response: missing task");
            }

            String status = text(ceTask, "status");
            emit(logLineConsumer, "CE task status: " + status);

            if ("SUCCESS".equalsIgnoreCase(status)) {
                break;
            }
            if ("FAILED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) {
                throw new IllegalStateException("SonarQube analysis execution failed (ceTask status=" + status + ")");
            }

            sleep(sleep);
        }

        String analysisId = text(ceTask, "analysisId");
        if (analysisId == null || analysisId.isBlank()) {
            throw new IllegalStateException("SonarQube CE task completed but analysisId is missing");
        }

        JsonNode qg = apiClient.getJson("/api/qualitygates/project_status", Map.of("analysisId", analysisId));
        JsonNode projectStatus = qg.get("projectStatus");
        if (projectStatus == null) {
            throw new IllegalStateException("Unexpected SonarQube response: missing projectStatus");
        }

        String qgStatus = text(projectStatus, "status");
        emit(logLineConsumer, "Quality Gate status: " + qgStatus);

        boolean passed = "OK".equalsIgnoreCase(qgStatus);

        return QualityGateResult.builder()
                .passed(passed)
                .qualityGateStatus(qgStatus)
                .analysisId(analysisId)
                .ceTaskId(ceTaskId)
                .build();
    }

    private Map<String, String> readReportTask(File reportTaskFile) {
        try {
            List<String> lines = Files.readAllLines(reportTaskFile.toPath());
            Map<String, String> map = new HashMap<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                map.put(key, value);
            }
            return map;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read report-task.txt: " + e.getMessage(), e);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(Math.max(200, duration.toMillis()));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for SonarQube results", ie);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }

    private void emit(Consumer<String> consumer, String line) {
        if (consumer != null) {
            consumer.accept(line);
        }
        log.debug("[QualityGate] {}", line);
    }

    @Value
    @Builder
    public static class QualityGateResult {
        boolean passed;
        String qualityGateStatus;
        String ceTaskId;
        String analysisId;
    }
}
