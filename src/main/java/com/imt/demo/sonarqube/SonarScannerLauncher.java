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
        String sonarToken = properties.getToken();
        if (sonarToken == null || sonarToken.isBlank()) {
            throw new IllegalStateException("Sonar token is not configured");
        }
        if (request.getWorkspaceDir() == null || !request.getWorkspaceDir().exists()) {
            throw new IllegalArgumentException("Workspace directory is missing");
        }
        StringBuilder sonarParams = new StringBuilder();

        sonarParams.append("\"-Dsonar.working.directory=/usr/src/.scannerwork\" ");
        sonarParams.append("\"-Dsonar.host.url=").append(request.getHostUrl()).append("\"");
        sonarParams.append(" \"-Dsonar.login=").append(request.getToken()).append("\"");
        sonarParams.append(" \"-Dsonar.projectKey=").append(request.getProjectKey()).append("\"");
        sonarParams.append(" \"-Dsonar.projectName=").append(request.getProjectName()).append("\"");

        if (request.getProjectVersion() != null && !request.getProjectVersion().isBlank()) {
            sonarParams.append(" \"-Dsonar.projectVersion=").append(request.getProjectVersion()).append("\"");
        }

        if (Files.exists(request.getWorkspaceDir().toPath().resolve("target/classes"))) {
            sonarParams.append(" \"-Dsonar.java.binaries=target/classes\"");
        }

        if (Files.exists(request.getWorkspaceDir().toPath().resolve("src"))) {
            sonarParams.append(" \"-Dsonar.sources=src\"");
        }

        sonarParams.append(" \"-Dsonar.projectBaseDir=/usr/src\"");

        List<String> command = new ArrayList<>(
                List.of("docker",
                        "compose",
                        "-f",
                        Path.of(properties.getDockerComposePath()).toAbsolutePath().normalize().toString(),
                        "run",
                        "--rm",
                        "-v",
                        "\"" + request.getWorkspaceDir().getAbsolutePath() + ":/usr/src:rw\"",
                        "-w",
                        "/usr/src",
                        "sonarscanner",
                        "sonar-scanner",
                        sonarParams.toString()));

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
