package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Étape 4: Analyse SonarQube
 */
@Slf4j
@Component
public class SonarQubeStep extends AbstractPipelineStep {

    @Override
    public String getName() {
        return "SonarQube Analysis";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        // Vérifier si SonarQube est configuré
        if (context.getSonarUrl() == null || context.getSonarToken() == null) {
            log.warn("SonarQube non configuré, étape ignorée");
            StepResult result = StepResult.builder()
                    .stepName(getName())
                    .status(com.imt.demo.model.StepStatus.SKIPPED)
                    .startTime(java.time.LocalDateTime.now())
                    .endTime(java.time.LocalDateTime.now())
                    .build();
            result.addLog("⚠ SonarQube non configuré - étape ignorée");
            return result;
        }

        // Commande SonarQube
        String[] command = {
            "mvn", "sonar:sonar",
            "-Dsonar.host.url=" + context.getSonarUrl(),
            "-Dsonar.token=" + context.getSonarToken(),
            "-Dsonar.projectKey=" + (context.getSonarProjectKey() != null ? context.getSonarProjectKey() : "jonk-back"),
            "-B"
        };

        return executeCommand(command, context.getWorkspaceDir(), context.getEnvironmentVariables());
    }

    @Override
    public boolean isCritical() {
        // L'analyse SonarQube n'est pas critique - le pipeline continue même en cas d'échec
        return false;
    }
}

