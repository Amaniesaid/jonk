package com.imt.demo.pipeline.model.steps;

import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.StepResult;
import com.imt.demo.pipeline.model.StepStatus;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AbstractPipelineStep implements PipelineStep {

    private StepResult stepResult;

    protected StepResult executeCommand(String[] command, String workingDirectory,
                                        Map<String, String> environmentVariables) {
        StepResult result = getInitialStepResult();

        try {
            log.info("Execution de la commande: {}", String.join(" ", command));
            result.addLog("Commande: " + String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            if (workingDirectory != null) {
                processBuilder.directory(new File(workingDirectory));
            }

            if (environmentVariables != null && !environmentVariables.isEmpty()) {
                processBuilder.environment().putAll(environmentVariables);
            }

            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.addLog(line);
                    log.debug("[{}] {}", getName(), line);
                }
            }

            int exitCode = process.waitFor();

            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();

            if (exitCode == 0) {
                result.setStatus(StepStatus.SUCCESS);
                result.addLog("Etape terminee avec succes (code: " + exitCode + ")");
                log.info("Etape '{}' terminee avec succes", getName());
            } else {
                result.setStatus(StepStatus.FAILED);
                result.setErrorMessage("Commande echouee avec le code de sortie: " + exitCode);
                result.addLog("Echec de l'etape (code: " + exitCode + ")");
                log.error("Etape '{}' echouee avec le code: {}", getName(), exitCode);
            }

        } catch (Exception e) {
            result.setStatus(StepStatus.FAILED);
            result.setErrorMessage("Exception: " + e.getMessage());
            result.addLog("Exception: " + e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();
            log.error("Erreur lors de l'execution de l'etape '{}'", getName(), e);
        }

        return result;
    }

    @Override
    public StepResult getInitialStepResult() {
        if (stepResult == null) {
            stepResult = StepResult.builder()
                    .stepName(getName())
                    .status(StepStatus.RUNNING)
                    .startTime(LocalDateTime.now())
                    .build();
        }

        return stepResult;
    }

    protected StepResult executeCommand(String[] command, String workingDirectory) {
        return executeCommand(command, workingDirectory, null);
    }

    protected StepResult executeCommands(List<String[]> commands, String workingDirectory,
                                         Map<String, String> environmentVariables) {
        StepResult result = getInitialStepResult();

        for (String[] command : commands) {
            StepResult commandResult = executeCommand(command, workingDirectory, environmentVariables);
            result.getLogs().addAll(commandResult.getLogs());

            if (commandResult.getStatus() == StepStatus.FAILED) {
                result.setStatus(StepStatus.FAILED);
                result.setErrorMessage(commandResult.getErrorMessage());
                result.setEndTime(LocalDateTime.now());
                result.calculateDuration();
                return result;
            }
        }

        result.setStatus(StepStatus.SUCCESS);
        result.setEndTime(LocalDateTime.now());
        result.calculateDuration();
        return result;
    }

    @Override
    public void rollback(PipelineContext context) throws Exception {
        log.info("Rollback de l'etape: {} (pas d'action specifique)", getName());
    }
}
