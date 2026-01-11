package com.imt.demo.pipeline.model.steps;

import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.StepResult;
import com.imt.demo.pipeline.model.StepStatus;
import com.imt.demo.sonarqube.QualityGateEvaluator;
import com.imt.demo.sonarqube.SonarProjectManager;
import com.imt.demo.sonarqube.SonarQubeProperties;
import com.imt.demo.sonarqube.SonarScannerLauncher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class SonarQubeStep extends AbstractPipelineStep {

    private final SonarQubeProperties properties;
    private final SonarScannerLauncher sonarScannerLauncher;
    private final SonarProjectManager sonarProjectManager;
    private final QualityGateEvaluator qualityGateEvaluator;

    public SonarQubeStep(
            SonarQubeProperties properties,
            SonarScannerLauncher sonarScannerLauncher,
            SonarProjectManager sonarProjectManager,
            QualityGateEvaluator qualityGateEvaluator
    ) {
        this.properties = properties;
        this.sonarScannerLauncher = sonarScannerLauncher;
        this.sonarProjectManager = sonarProjectManager;
        this.qualityGateEvaluator = qualityGateEvaluator;
    }

    @Override
    public String getName() {
        return "SonarQube Analysis";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        StepResult result = StepResult.builder()
                .stepName(getName())
                .status(StepStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .build();

        if (!Boolean.TRUE.equals(context.getSonarEnabled())) {
            result.setStatus(StepStatus.SKIPPED);
            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();
            result.addLog("Etape SonarQube desactivee (sonarEnabled=false)");
            return result;
        }

        String hostUrl = properties.getHostUrl();
        String token = properties.getToken();
        if (hostUrl == null || hostUrl.isBlank() || token == null || token.isBlank()) {
            result.setStatus(StepStatus.FAILED);
            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();
            result.setErrorMessage("SonarQube non configure cote serveur (jonk.sonarqube.host-url/token)");
            result.addLog("Configuration manquante: jonk.sonarqube.host-url et/ou jonk.sonarqube.token");
            return result;
        }

        String projectKey = sonarProjectManager.computeProjectKey(context.getWorkspaceDirFile(), context.getGitUrl());
        context.setSonarProjectKey(projectKey);

        String projectName = projectKey;
        result.addLog("SonarQube host: " + hostUrl);
        result.addLog("SonarQube projectKey: " + projectKey);

        try {
            sonarProjectManager.ensureProjectExists(projectKey, projectName);
        } catch (Exception e) {
            result.setStatus(StepStatus.FAILED);
            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();
            result.setErrorMessage("Impossible de verifier/creer le projet SonarQube: " + e.getMessage());
            result.addLog("Erreur API SonarQube: " + e.getMessage());
            return result;
        }

        SonarScannerLauncher.ScanExecutionResult scanExecution;
        try {
            scanExecution = sonarScannerLauncher.launch(
                    SonarScannerLauncher.ScanRequest.builder()
                        .workspaceDir(context.getWorkspaceDirFile())
                            .hostUrl(hostUrl)
                            .token(token)
                            .projectKey(projectKey)
                            .projectName(projectName)
                            .projectVersion(context.getCommitHash())
                            .environment(context.getEnvironmentVariables())
                            .build(),
                    result::addLog
            );
        } catch (Exception e) {
            result.setStatus(StepStatus.FAILED);
            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();
            result.setErrorMessage("Analyse SonarScanner echouee: " + e.getMessage());
            return result;
        }

        try {
            var qg = qualityGateEvaluator.evaluateFromReportTask(scanExecution.getReportTaskFile(), result::addLog);
            if (!qg.isPassed()) {
                result.setStatus(StepStatus.FAILED);
                result.setErrorMessage("Quality Gate KO: " + qg.getQualityGateStatus());
                result.addLog("Quality Gate KO -> echec du pipeline");
            } else {
                result.setStatus(StepStatus.SUCCESS);
                result.addLog("Quality Gate OK");
            }
        } catch (Exception e) {
            result.setStatus(StepStatus.FAILED);
            result.setErrorMessage("Impossible d'evaluer le Quality Gate: " + e.getMessage());
            result.addLog("Erreur Quality Gate: " + e.getMessage());
        }

        result.setEndTime(LocalDateTime.now());
        result.calculateDuration();
        return result;
    }

    @Override
    public boolean isCritical() {
        return true;
    }
}
