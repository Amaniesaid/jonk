package com.imt.demo.service;

import com.imt.demo.engine.PipelineEngine;
import com.imt.demo.model.*;
import com.imt.demo.repository.PipelineExecutionRepository;
import com.imt.demo.steps.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service principal gérant la logique métier du pipeline CI/CD
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineEngine pipelineEngine;
    private final PipelineExecutionRepository executionRepository;

    // Injection des étapes du pipeline
    private final GitCloneStep gitCloneStep;
    private final MavenBuildStep mavenBuildStep;
    private final MavenTestStep mavenTestStep;
    private final SonarQubeStep sonarQubeStep;
    private final DockerBuildStep dockerBuildStep;
    private final DockerScanStep dockerScanStep;
    private final DockerDeployStep dockerDeployStep;
    private final HealthCheckStep healthCheckStep;

    /**
     * Lance un pipeline de manière asynchrone
     * @param context Contexte du pipeline
     * @return ID de l'exécution
     */
    @Async("pipelineExecutor")
    public String runPipelineAsync(PipelineContext context) {
        // Générer un ID unique pour cette exécution
        String executionId = UUID.randomUUID().toString();
        context.setExecutionId(executionId);
        context.setPipelineId(executionId);

        log.info("🚀 Démarrage asynchrone du pipeline: {}", executionId);

        // Créer l'exécution initiale dans la base de données
        PipelineExecution execution = PipelineExecution.builder()
                .id(executionId)
                .gitRepoUrl(context.getGitUrl())
                .gitBranch(context.getBranch())
                .triggeredBy(context.getTriggeredBy())
                .status(PipelineStatus.PENDING)
                .startTime(LocalDateTime.now())
                .steps(new ArrayList<>())
                .build();

        executionRepository.save(execution);

        // Valider le contexte
        try {
            pipelineEngine.validateContext(context);
        } catch (IllegalArgumentException e) {
            log.error("❌ Validation du contexte échouée: {}", e.getMessage());
            execution.setStatus(PipelineStatus.FAILED);
            execution.setErrorMessage("Validation échouée: " + e.getMessage());
            execution.setEndTime(LocalDateTime.now());
            execution.calculateDuration();
            executionRepository.save(execution);
            return executionId;
        }

        // Construire la liste des étapes à exécuter
        List<PipelineStep> steps = buildPipelineSteps(context);

        try {
            // Exécuter le pipeline
            PipelineExecution result = pipelineEngine.executePipeline(context, steps);

            // Mettre à jour avec les résultats
            execution.setStatus(result.getStatus());
            execution.setSteps(result.getSteps());
            execution.setErrorMessage(result.getErrorMessage());
            execution.setEndTime(result.getEndTime());
            execution.setCommitHash(context.getCommitHash());
            execution.calculateDuration();

            executionRepository.save(execution);

            log.info("✅ Pipeline terminé: {} - Statut: {}", executionId, execution.getStatus());

        } catch (Exception e) {
            log.error("💥 Erreur lors de l'exécution du pipeline: {}", executionId, e);
            execution.setStatus(PipelineStatus.FAILED);
            execution.setErrorMessage("Exception: " + e.getMessage());
            execution.setEndTime(LocalDateTime.now());
            execution.calculateDuration();
            executionRepository.save(execution);
        }

        return executionId;
    }

    /**
     * Lance un pipeline de manière synchrone (pour tests)
     */
    public PipelineExecution runPipelineSync(PipelineContext context) {
        String executionId = UUID.randomUUID().toString();
        context.setExecutionId(executionId);
        context.setPipelineId(executionId);

        // Valider le contexte
        pipelineEngine.validateContext(context);

        // Construire la liste des étapes
        List<PipelineStep> steps = buildPipelineSteps(context);

        // Exécuter le pipeline
        PipelineExecution execution = pipelineEngine.executePipeline(context, steps);
        execution.setId(executionId);

        // Sauvegarder dans la base de données
        return executionRepository.save(execution);
    }

    /**
     * Construit la liste ordonnée des étapes du pipeline
     */
    private List<PipelineStep> buildPipelineSteps(PipelineContext context) {
        List<PipelineStep> steps = new ArrayList<>();

        // 1. Clone du repository Git
        steps.add(gitCloneStep);

        // 2. Build Maven
        steps.add(mavenBuildStep);

        // 3. Tests unitaires
        steps.add(mavenTestStep);

        // 4. Analyse SonarQube (optionnelle)
        if (context.getSonarQubeUrl() != null) {
            steps.add(sonarQubeStep);
        }

        // 5. Build de l'image Docker
        steps.add(dockerBuildStep);

        // 6. Scan de sécurité (optionnel)
        steps.add(dockerScanStep);

        // 7. Déploiement
        if (context.getDeploymentPort() != null) {
            steps.add(dockerDeployStep);

            // 8. Health check
            steps.add(healthCheckStep);
        }

        log.info("📋 Pipeline configuré avec {} étapes", steps.size());
        return steps;
    }

    /**
     * Récupère une exécution par son ID
     */
    public Optional<PipelineExecution> getExecution(String executionId) {
        return executionRepository.findById(executionId);
    }

    /**
     * Récupère toutes les exécutions récentes
     */
    public List<PipelineExecution> getRecentExecutions() {
        return executionRepository.findTop10ByOrderByStartTimeDesc();
    }

    /**
     * Récupère les exécutions par statut
     */
    public List<PipelineExecution> getExecutionsByStatus(PipelineStatus status) {
        return executionRepository.findByStatus(status);
    }

    /**
     * Récupère les logs d'une exécution
     */
    public List<String> getExecutionLogs(String executionId) {
        Optional<PipelineExecution> execution = executionRepository.findById(executionId);

        if (execution.isEmpty()) {
            return List.of("Exécution non trouvée");
        }

        List<String> allLogs = new ArrayList<>();
        allLogs.add("═══════════════════════════════════════════════════════════");
        allLogs.add("📋 LOGS DU PIPELINE: " + executionId);
        allLogs.add("═══════════════════════════════════════════════════════════");
        allLogs.add("");

        PipelineExecution exec = execution.get();
        allLogs.add("🔗 Repository: " + exec.getGitRepoUrl());
        allLogs.add("🌿 Branche: " + exec.getGitBranch());
        allLogs.add("📦 Commit: " + (exec.getCommitHash() != null ? exec.getCommitHash() : "N/A"));
        allLogs.add("👤 Déclenché par: " + exec.getTriggeredBy());
        allLogs.add("📊 Statut: " + exec.getStatus());
        allLogs.add("⏱️  Durée: " + (exec.getDurationMs() != null ? exec.getDurationMs() + "ms" : "N/A"));
        allLogs.add("");

        // Logs de chaque étape
        if (exec.getSteps() != null) {
            for (StepResult step : exec.getSteps()) {
                allLogs.add("───────────────────────────────────────────────────────────");
                allLogs.add("📌 ÉTAPE: " + step.getStepName());
                allLogs.add("   Statut: " + step.getStatus());
                allLogs.add("   Durée: " + (step.getDurationMs() != null ? step.getDurationMs() + "ms" : "N/A"));
                allLogs.add("───────────────────────────────────────────────────────────");

                if (step.getLogs() != null) {
                    allLogs.addAll(step.getLogs());
                }

                if (step.getErrorMessage() != null) {
                    allLogs.add("❌ ERREUR: " + step.getErrorMessage());
                }

                allLogs.add("");
            }
        }

        allLogs.add("═══════════════════════════════════════════════════════════");
        allLogs.add("FIN DES LOGS");
        allLogs.add("═══════════════════════════════════════════════════════════");

        return allLogs;
    }

    /**
     * Annule une exécution en cours (si possible)
     */
    public boolean cancelExecution(String executionId) {
        Optional<PipelineExecution> execution = executionRepository.findById(executionId);

        if (execution.isEmpty()) {
            return false;
        }

        PipelineExecution exec = execution.get();

        if (exec.getStatus() == PipelineStatus.RUNNING) {
            exec.setStatus(PipelineStatus.CANCELLED);
            exec.setEndTime(LocalDateTime.now());
            exec.calculateDuration();
            executionRepository.save(exec);
            log.warn("⚠️  Pipeline annulé: {}", executionId);
            return true;
        }

        return false;
    }
}
