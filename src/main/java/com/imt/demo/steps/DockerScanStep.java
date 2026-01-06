package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Étape 6: Scan de sécurité de l'image Docker avec Trivy
 */
@Slf4j
@Component
public class DockerScanStep extends AbstractPipelineStep {

    @Override
    public String getName() {
        return "Docker Security Scan";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        String fullImageName = context.getDockerImageName() + ":" + context.getDockerImageTag();

        // Vérifier si Trivy est installé
        String[] checkTrivy = {"which", "trivy"};
        StepResult checkResult = executeCommand(checkTrivy, null);

        if (checkResult.getStatus() != com.imt.demo.model.StepStatus.SUCCESS) {
            log.warn("Trivy n'est pas installé, scan de sécurité ignoré");
            StepResult result = StepResult.builder()
                    .stepName(getName())
                    .status(com.imt.demo.model.StepStatus.SKIPPED)
                    .startTime(java.time.LocalDateTime.now())
                    .endTime(java.time.LocalDateTime.now())
                    .build();
            result.addLog("⚠ Trivy non installé - scan de sécurité ignoré");
            result.addLog("💡 Pour installer Trivy: brew install trivy (macOS) ou voir https://aquasecurity.github.io/trivy/");
            return result;
        }

        // Commande Trivy - scan avec sévérité moyenne et haute
        String[] command = {
            "trivy", "image",
            "--severity", "MEDIUM,HIGH,CRITICAL",
            "--exit-code", "0",  // Ne pas échouer sur les vulnérabilités (pour démo)
            fullImageName
        };

        StepResult result = executeCommand(command, null);

        if (result.getStatus() == com.imt.demo.model.StepStatus.SUCCESS) {
            result.addLog("🔒 Scan de sécurité terminé");
        }

        return result;
    }

    @Override
    public boolean isCritical() {
        // Le scan de sécurité n'est pas critique pour la démo
        return false;
    }
}

