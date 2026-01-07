package com.imt.demo.engine;

import com.imt.demo.model.*;
import com.imt.demo.steps.PipelineStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Moteur d'orchestration du pipeline CI/CD.
 * Responsable de l'exécution séquentielle des étapes, la gestion des erreurs et le rollback.
 */
@Slf4j
@Component
public class PipelineEngine {

    /**
     * Exécute un pipeline complet avec gestion des erreurs et rollback automatique
     */
    public PipelineExecution executePipeline(PipelineContext context, List<PipelineStep> steps) {
        // Créer l'exécution du pipeline
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

        log.info("═══════════════════════════════════════════════════════════");
        log.info(" Démarrage du pipeline: {}", execution.getId());
        log.info(" Repository: {}", context.getGitUrl());
        log.info(" Branche: {}", context.getBranch());
        log.info("═══════════════════════════════════════════════════════════");

        // Préparer le workspace
        try {
            prepareWorkspace(context);
        } catch (Exception e) {
            log.error(" Erreur lors de la préparation du workspace", e);
            execution.setStatus(PipelineStatus.FAILED);
            execution.setErrorMessage("Échec de la préparation du workspace: " + e.getMessage());
            execution.setEndTime(LocalDateTime.now());
            execution.calculateDuration();
            return execution;
        }

        // Liste pour stocker les étapes exécutées avec succès (pour le rollback)
        List<PipelineStep> executedSteps = new ArrayList<>();
        boolean pipelineSuccess = true;
        String failedStepName = null;

        // Exécuter chaque étape séquentiellement
        for (PipelineStep step : steps) {
            log.info("───────────────────────────────────────────────────────────");
            log.info("  Exécution de l'étape: {}", step.getName());
            log.info("───────────────────────────────────────────────────────────");

            StepResult stepResult;
            try {
                // Exécuter l'étape
                stepResult = step.execute(context);
                execution.addStepResult(stepResult);

                if (stepResult.getStatus() == StepStatus.SUCCESS) {
                    log.info(" Étape '{}' terminée avec succès en {}ms",
                            step.getName(), stepResult.getDurationMs());
                    executedSteps.add(step);
                } else {
                    log.error(" Étape '{}' échouée: {}",
                            step.getName(), stepResult.getErrorMessage());
                    pipelineSuccess = false;
                    failedStepName = step.getName();
                    break; // Arrêter l'exécution
                }

            } catch (Exception e) {
                log.error(" Exception non gérée dans l'étape '{}'", step.getName(), e);

                // Créer un résultat d'erreur
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

        // Terminer le pipeline
        execution.setEndTime(LocalDateTime.now());
        execution.calculateDuration();

        if (pipelineSuccess) {
            execution.setStatus(PipelineStatus.SUCCESS);
            log.info("═══════════════════════════════════════════════════════════");
            log.info(" Pipeline terminé avec SUCCÈS en {}ms", execution.getDurationMs());
            log.info("═══════════════════════════════════════════════════════════");
        } else {
            execution.setStatus(PipelineStatus.FAILED);
            execution.setErrorMessage("Échec à l'étape: " + failedStepName);

            log.error("═══════════════════════════════════════════════════════════");
            log.error(" Pipeline ÉCHOUÉ à l'étape: {}", failedStepName);
            log.error("═══════════════════════════════════════════════════════════");

            // Exécuter le rollback si nécessaire
            if (!executedSteps.isEmpty()) {
                log.warn(" Démarrage du rollback...");
                performRollback(context, executedSteps, execution);
            }
        }

        // Nettoyer le workspace
        cleanupWorkspace(context);

        return execution;
    }

    /**
     * Prépare le workspace temporaire pour l'exécution du pipeline
     */
    private void prepareWorkspace(PipelineContext context) throws Exception {
        String workspaceBase = System.getProperty("java.io.tmpdir") + "/jonk-pipelines";
        String workspaceDir = workspaceBase + "/" + UUID.randomUUID().toString();

        File workspace = new File(workspaceDir);
        if (!workspace.mkdirs()) {
            throw new RuntimeException("Impossible de créer le workspace: " + workspaceDir);
        }

        context.setWorkspaceDirectory(workspaceDir);
        context.setWorkspaceDir(workspace);

        log.info("📂 Workspace créé: {}", workspaceDir);
    }

    /**
     * Effectue le rollback des étapes exécutées en cas d'échec
     */
    private void performRollback(PipelineContext context, List<PipelineStep> executedSteps,
                                  PipelineExecution execution) {
        log.warn("═══════════════════════════════════════════════════════════");
        log.warn(" ROLLBACK EN COURS");
        log.warn("═══════════════════════════════════════════════════════════");

        // Exécuter le rollback dans l'ordre inverse
        for (int i = executedSteps.size() - 1; i >= 0; i--) {
            PipelineStep step = executedSteps.get(i);

            // Ne faire le rollback que pour les étapes critiques
            if (step.isCritical()) {
                try {
                    log.info("  Rollback de l'étape: {}", step.getName());
                    step.rollback(context);
                    log.info(" Rollback de '{}' réussi", step.getName());
                } catch (Exception e) {
                    log.error(" Erreur lors du rollback de '{}': {}", step.getName(), e.getMessage(), e);
                    // Continuer le rollback même en cas d'erreur
                }
            }
        }

        log.warn("═══════════════════════════════════════════════════════════");
        log.warn(" ROLLBACK TERMINÉ");
        log.warn("═══════════════════════════════════════════════════════════");
    }

    /**
     * Nettoie le workspace temporaire après l'exécution
     */
    private void cleanupWorkspace(PipelineContext context) {
        if (context.getWorkspaceDirectory() != null) {
            try {
                Path workspacePath = Path.of(context.getWorkspaceDirectory());
                if (Files.exists(workspacePath)) {
                    log.info(" Nettoyage du workspace...");
                    deleteDirectory(workspacePath.toFile());
                    log.info(" Workspace nettoyé");
                }
            } catch (Exception e) {
                log.warn("  Impossible de nettoyer le workspace: {}", e.getMessage());
            }
        }
    }

    /**
     * Supprime récursivement un répertoire
     */
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

    /**
     * Valide le contexte du pipeline avant l'exécution
     */
    public void validateContext(PipelineContext context) throws IllegalArgumentException {
        if (context.getGitUrl() == null || context.getGitUrl().isEmpty()) {
            throw new IllegalArgumentException("L'URL du dépôt Git est obligatoire");
        }
        if (context.getBranch() == null || context.getBranch().isEmpty()) {
            throw new IllegalArgumentException("La branche est obligatoire");
        }
        if (context.getDockerImageName() == null || context.getDockerImageName().isEmpty()) {
            throw new IllegalArgumentException("Le nom de l'image Docker est obligatoire");
        }
    }
}
