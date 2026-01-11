package com.imt.demo.sonarqube;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class SonarScannerLauncher {

    private final SonarQubeProperties properties;

    public SonarScannerLauncher(SonarQubeProperties properties) {
        this.properties = properties;
    }

    public ScanExecutionResult launch(ScanRequest request, Consumer<String> logLineConsumer) {
        String scanner = properties.getScannerCommand();
        if (scanner == null || scanner.isBlank()) {
            throw new IllegalStateException("SonarScanner command is not configured");
        }
        if (request.getWorkspaceDir() == null || !request.getWorkspaceDir().exists()) {
            throw new IllegalArgumentException("Workspace directory is missing");
        }

        List<String> command = new ArrayList<>();
        command.add(scanner);
        command.add("-Dsonar.host.url=" + request.getHostUrl());
        command.add("-Dsonar.login=" + request.getToken());
        command.add("-Dsonar.projectKey=" + request.getProjectKey());
        command.add("-Dsonar.projectName=" + request.getProjectName());

        if (request.getProjectVersion() != null && !request.getProjectVersion().isBlank()) {
            command.add("-Dsonar.projectVersion=" + request.getProjectVersion());
        }

        Path targetClasses = request.getWorkspaceDir().toPath().resolve("target/classes");
        if (Files.exists(targetClasses)) {
            command.add("-Dsonar.java.binaries=target/classes");
        }

        Path sourcesDir = request.getWorkspaceDir().toPath().resolve("src");
        if (Files.exists(sourcesDir)) {
            command.add("-Dsonar.sources=src");
        }

        command.add("-Dsonar.projectBaseDir=" + request.getWorkspaceDir().getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(request.getWorkspaceDir());
        pb.redirectErrorStream(true);

        if (request.getEnvironment() != null && !request.getEnvironment().isEmpty()) {
            pb.environment().putAll(request.getEnvironment());
        }

        LocalDateTime start = LocalDateTime.now();
        emit(logLineConsumer, "Debut analyse SonarScanner (projectKey=" + request.getProjectKey() + ")");
        emit(logLineConsumer, "Commande: " + String.join(" ", command));

        try {
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    emit(logLineConsumer, line);
                }
            }

            int exitCode = process.waitFor();
            LocalDateTime end = LocalDateTime.now();

            if (exitCode != 0) {
                throw new IllegalStateException("SonarScanner failed with exit code " + exitCode);
            }

            Path reportTaskPath = request.getWorkspaceDir().toPath().resolve(".scannerwork/report-task.txt");
            if (!Files.exists(reportTaskPath)) {
                throw new IllegalStateException("Sonar report-task.txt not found (expected at .scannerwork/report-task.txt)");
            }

            return new ScanExecutionResult(exitCode, reportTaskPath.toFile(), start, end);
        } catch (Exception e) {
            emit(logLineConsumer, "Erreur analyse Sonar: " + e.getMessage());
            throw new IllegalStateException("SonarScanner execution error: " + e.getMessage(), e);
        } finally {
            emit(logLineConsumer, "Fin analyse SonarScanner");
        }
    }

    private void emit(Consumer<String> consumer, String line) {
        if (consumer != null) {
            consumer.accept(line);
        }
        log.debug("[SonarScanner] {}", line);
    }

    @Value
    public static class ScanExecutionResult {
        int exitCode;
        File reportTaskFile;
        LocalDateTime startTime;
        LocalDateTime endTime;
    }

    @Value
    @Builder
    public static class ScanRequest {
        File workspaceDir;
        String hostUrl;
        String token;
        String projectKey;
        String projectName;
        String projectVersion;
        Map<String, String> environment;
    }
}
