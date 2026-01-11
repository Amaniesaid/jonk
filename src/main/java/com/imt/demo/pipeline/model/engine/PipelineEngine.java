package com.imt.demo.pipeline.model.engine;

import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.PipelineExecution;
import com.imt.demo.pipeline.model.PipelineStatus;
import com.imt.demo.pipeline.model.StepResult;
import com.imt.demo.pipeline.model.StepStatus;
import com.imt.demo.pipeline.model.steps.PipelineStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class PipelineEngine {

    public PipelineExecution executePipeline(PipelineContext context, List<PipelineStep> steps) {
        PipelineExecution execution = PipelineExecution.builder()
                .id(UUID.randomUUID().toString())
                .gitRepoUrl(context.getGitUrl())
                .gitBranch(context.getBranch())
                .commitHash(context.getCommitHash())
                .status(PipelineStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .triggeredBy(context.getTriggeredBy())
                .steps(new ArrayList<>())
                .build();

        log.info("===============================================================");
        log.info("Demarrage du pipeline: {}", execution.getId());
        log.info("Repository: {}", context.getGitUrl());
        log.info("Branche: {}", context.getBranch());
        log.info("===============================================================");

        try {
            prepareWorkspace(context);
        } catch (Exception e) {
            log.error("Erreur lors de la preparation du workspace", e);
            execution.setStatus(PipelineStatus.FAILED);
            execution.setErrorMessage("Echec de la preparation du workspace: " + e.getMessage());
            execution.setEndTime(LocalDateTime.now());
            execution.calculateDuration();
            return execution;
        }

        List<PipelineStep> executedSteps = new ArrayList<>();
        boolean pipelineSuccess = true;
        String failedStepName = null;

        for (PipelineStep step : steps) {
            log.info("---------------------------------------------------------------");
            log.info("Execution de l'etape: {}", step.getName());
            log.info("---------------------------------------------------------------");

            StepResult stepResult;
            try {
                stepResult = step.execute(context);
                execution.addStepResult(stepResult);

                if (stepResult.getStatus() == StepStatus.SUCCESS) {
                    log.info("Etape '{}' terminee avec succes en {}ms", step.getName(), stepResult.getDurationMs());
                    executedSteps.add(step);
                } else if (stepResult.getStatus() == StepStatus.SKIPPED) {
                    log.warn("Etape '{}' ignoree (SKIPPED)", step.getName());
                } else {
                    log.error("Etape '{}' echouee: {}", step.getName(), stepResult.getErrorMessage());
                    pipelineSuccess = false;
                    failedStepName = step.getName();
                    break;
                }

            } catch (Exception e) {
                log.error("Exception non geree dans l'etape '{}'", step.getName(), e);

                stepResult = StepResult.builder()
                        .stepName(step.getName())
                        .status(StepStatus.FAILED)
                        .errorMessage("Exception: " + e.getMessage())
                        .startTime(LocalDateTime.now())
                        .endTime(LocalDateTime.now())
                        .build();
                stepResult.addLog("Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                execution.addStepResult(stepResult);

                pipelineSuccess = false;
                failedStepName = step.getName();
                break;
            }
        }

        execution.setEndTime(LocalDateTime.now());
        execution.calculateDuration();

        if (pipelineSuccess) {
            execution.setStatus(PipelineStatus.SUCCESS);
            log.info("===============================================================");
            log.info("Pipeline termine avec SUCCES en {}ms", execution.getDurationMs());
            log.info("===============================================================");
        } else {
            execution.setStatus(PipelineStatus.FAILED);
            execution.setErrorMessage("Echec a l'etape: " + failedStepName);

            log.error("===============================================================");
            log.error("Pipeline ECHOUE a l'etape: {}", failedStepName);
            log.error("===============================================================");

            if (!executedSteps.isEmpty()) {
                log.warn("Demarrage du rollback...");
                performRollback(context, executedSteps);
            }
        }

        cleanupWorkspace(context);

        return execution;
    }

    private void prepareWorkspace(PipelineContext context) throws Exception {
        String workspaceBase = System.getProperty("java.io.tmpdir") + "/jonk-pipelines";
        String workspaceDir = workspaceBase + "/" + UUID.randomUUID().toString();

        File workspace = new File(workspaceDir);
        if (!workspace.mkdirs()) {
            throw new RuntimeException("Impossible de creer le workspace: " + workspaceDir);
        }

        context.setWorkspaceDirectory(workspaceDir);
        context.setWorkspaceDir(workspace);

        log.info("Workspace cree: {}", workspaceDir);
    }

    private void performRollback(PipelineContext context, List<PipelineStep> executedSteps) {
        log.warn("===============================================================");
        log.warn("ROLLBACK EN COURS");
        log.warn("===============================================================");

        for (int i = executedSteps.size() - 1; i >= 0; i--) {
            PipelineStep step = executedSteps.get(i);

            if (step.isCritical()) {
                try {
                    log.info("Rollback de l'etape: {}", step.getName());
                    step.rollback(context);
                    log.info("Rollback de '{}' reussi", step.getName());
                } catch (Exception e) {
                    log.error("Erreur lors du rollback de '{}': {}", step.getName(), e.getMessage(), e);
                }
            }
        }

        log.warn("===============================================================");
        log.warn("ROLLBACK TERMINE");
        log.warn("===============================================================");
    }

    private void cleanupWorkspace(PipelineContext context) {
        if (context.getWorkspaceDirectory() != null) {
            try {
                Path workspacePath = Path.of(context.getWorkspaceDirectory());
                if (Files.exists(workspacePath)) {
                    log.info("Nettoyage du workspace...");
                    deleteDirectory(workspacePath.toFile());
                    log.info("Workspace nettoye");
                }
            } catch (Exception e) {
                log.warn("Impossible de nettoyer le workspace: {}", e.getMessage());
            }
        }
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    public void validateContext(PipelineContext context) throws IllegalArgumentException {
        if (context.getGitUrl() == null || context.getGitUrl().isEmpty()) {
            throw new IllegalArgumentException("L'URL du depot Git est obligatoire");
        }
        if (context.getBranch() == null || context.getBranch().isEmpty()) {
            throw new IllegalArgumentException("La branche est obligatoire");
        }
        if (context.getDockerImageName() == null || context.getDockerImageName().isEmpty()) {
            throw new IllegalArgumentException("Le nom de l'image Docker est obligatoire");
        }
    }
}
