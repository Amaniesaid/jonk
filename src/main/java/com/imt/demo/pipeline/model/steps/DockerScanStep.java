package com.imt.demo.pipeline.model.steps;

import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.StepResult;
import com.imt.demo.pipeline.model.StepStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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

        if (!isTrivyInstalled()) {
            log.warn("Trivy n'est pas installe, scan de securite ignore");
            StepResult result = StepResult.builder()
                    .stepName(getName())
                    .status(StepStatus.SKIPPED)
                    .startTime(LocalDateTime.now())
                    .endTime(LocalDateTime.now())
                    .build();
            result.addLog("Trivy non installe - scan de securite ignore");
            result.addLog("Pour installer Trivy:");
            result.addLog("  - macOS: brew install trivy");
            result.addLog("  - Linux: voir https://aquasecurity.github.io/trivy/");
            result.calculateDuration();
            return result;
        }

        String[] command = {
            "trivy", "image",
            "--severity", "MEDIUM,HIGH,CRITICAL",
            "--exit-code", "0",
            fullImageName
        };

        StepResult result = executeCommand(command, null);

        if (result.getStatus() == StepStatus.SUCCESS) {
            result.addLog("Scan de securite termine");
        }

        return result;
    }

    @Override
    public boolean isCritical() {
        return false;
    }

    private boolean isTrivyInstalled() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("which", "trivy");
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.debug("Erreur lors de la verification de Trivy: {}", e.getMessage());
            return false;
        }
    }
}
