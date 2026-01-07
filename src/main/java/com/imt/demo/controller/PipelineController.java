package com.imt.demo.controller;

import com.imt.demo.dto.PipelineRequest;
import com.imt.demo.dto.PipelineResponse;
import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.PipelineExecution;
import com.imt.demo.model.PipelineStatus;
import com.imt.demo.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Contrôleur REST pour gérer les pipelines CI/CD
 */
@Slf4j
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;

    /**
     * Endpoint pour déclencher un nouveau pipeline
     * POST /api/pipeline/run
     */
    @PostMapping("/run")
//    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<Map<String, String>> runPipeline(@RequestBody PipelineRequest request) {
        log.info(" Requête de déclenchement de pipeline reçue");
        log.info("   Git URL: {}", request.getGitUrl());
        log.info("   Branche: {}", request.getBranch());

        // Validation de base
        if (request.getGitUrl() == null || request.getGitUrl().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "L'URL du dépôt Git est obligatoire"));
        }

        if (request.getBranch() == null || request.getBranch().isEmpty()) {
            request.setBranch("main"); // Branche par défaut
        }

        if (request.getDockerImageName() == null || request.getDockerImageName().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Le nom de l'image Docker est obligatoire"));
        }

        try {
            // Convertir la requête en contexte
            PipelineContext context = buildContextFromRequest(request);

            // Lancer le pipeline de manière asynchrone
            String executionId = pipelineService.runPipelineAsync(context).join();

            log.info(" Pipeline lancé avec succès: {}", executionId);

            Map<String, String> response = new HashMap<>();
            response.put("executionId", executionId);
            response.put("message", "Pipeline démarré avec succès");
            response.put("status", "RUNNING");

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error(" Erreur lors du lancement du pipeline", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors du lancement: " + e.getMessage()));
        }
    }

    /**
     * Récupère les détails d'une exécution de pipeline
     * GET /api/pipeline/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'VIEWER')")
    public ResponseEntity<?> getPipeline(@PathVariable String id) {
        log.info(" Récupération du pipeline: {}", id);

        Optional<PipelineExecution> execution = pipelineService.getExecution(id);

        if (execution.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PipelineResponse response = PipelineResponse.fromExecution(execution.get(), true);
        return ResponseEntity.ok(response);
    }

    /**
     * Récupère les logs d'une exécution
     * GET /api/pipeline/{id}/logs
     */
    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'VIEWER')")
    public ResponseEntity<?> getPipelineLogs(@PathVariable String id) {
        log.info(" Récupération des logs du pipeline: {}", id);

        Optional<PipelineExecution> execution = pipelineService.getExecution(id);

        if (execution.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<String> logs = pipelineService.getExecutionLogs(id);
        return ResponseEntity.ok(Map.of(
                "executionId", id,
                "logs", logs,
                "status", execution.get().getStatus()
        ));
    }

    /**
     * Liste toutes les exécutions récentes
     * GET /api/pipeline/executions
     */
    @GetMapping("/executions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'VIEWER')")
    public ResponseEntity<List<PipelineResponse>> getRecentExecutions() {
        log.info(" Récupération des exécutions récentes");

        List<PipelineExecution> executions = pipelineService.getRecentExecutions();
        List<PipelineResponse> responses = executions.stream()
                .map(exec -> PipelineResponse.fromExecution(exec, false))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Récupère les exécutions par statut
     * GET /api/pipeline/executions/status/{status}
     */
    @GetMapping("/executions/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'VIEWER')")
    public ResponseEntity<List<PipelineResponse>> getExecutionsByStatus(@PathVariable String status) {
        log.info(" Récupération des exécutions avec statut: {}", status);

        try {
            PipelineStatus pipelineStatus = PipelineStatus.valueOf(status.toUpperCase());
            List<PipelineExecution> executions = pipelineService.getExecutionsByStatus(pipelineStatus);
            List<PipelineResponse> responses = executions.stream()
                    .map(exec -> PipelineResponse.fromExecution(exec, false))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Annule une exécution en cours
     * POST /api/pipeline/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<?> cancelPipeline(@PathVariable String id) {
        log.info("  Demande d'annulation du pipeline: {}", id);

        boolean cancelled = pipelineService.cancelExecution(id);

        if (cancelled) {
            return ResponseEntity.ok(Map.of(
                    "message", "Pipeline annulé avec succès",
                    "executionId", id
            ));
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Impossible d'annuler ce pipeline (n'existe pas ou n'est pas en cours)"));
        }
    }

    /**
     * Endpoint de santé pour vérifier que l'API fonctionne
     * GET /api/pipeline/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Jonk CI/CD Engine",
                "version", "1.0.0"
        ));
    }

    /**
     * Convertit une requête en contexte de pipeline
     */
    private PipelineContext buildContextFromRequest(PipelineRequest request) {
        return PipelineContext.builder()
                .gitUrl(request.getGitUrl())
                .branch(request.getBranch())
                .buildTool(request.getBuildTool() != null ? request.getBuildTool() : "maven")
                .dockerImageName(request.getDockerImageName())
                .dockerImageTag(request.getDockerImageTag() != null ? request.getDockerImageTag() : "latest-" + System.currentTimeMillis())
                .dockerRegistry(request.getDockerRegistry())
                .sonarQubeUrl(request.getSonarQubeUrl())
                .sonarQubeToken(request.getSonarQubeToken())
                .sonarProjectKey(request.getSonarProjectKey())
                .deploymentHost(request.getDeploymentHost())
                .deploymentUser(request.getDeploymentUser())
                .deploymentPort(request.getDeploymentPort())
                .sshUser(request.getDeploymentUser())
                .sshKeyPath(request.getSshKeyPath())
                .environmentVariables(request.getEnvironmentVariables() != null ? request.getEnvironmentVariables() : new HashMap<>())
                .triggeredBy(request.getTriggeredBy() != null ? request.getTriggeredBy() : "anonymous")
                .build();
    }
}
