package com.imt.demo.pipeline.controller;

import com.imt.demo.pipeline.dto.PipelineRequest;
import com.imt.demo.pipeline.dto.PipelineResponse;
import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.PipelineExecution;
import com.imt.demo.pipeline.model.PipelineStatus;
import com.imt.demo.pipeline.service.PipelineService;
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

@Slf4j
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;


    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'VIEWER')")
    public ResponseEntity<?> getPipeline(@PathVariable String id) {
        log.info("Recuperation du pipeline: {}", id);

        Optional<PipelineExecution> execution = pipelineService.getExecution(id);

        if (execution.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PipelineResponse response = PipelineResponse.fromExecution(execution.get(), true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'VIEWER')")
    public ResponseEntity<?> getPipelineLogs(@PathVariable String id) {
        log.info("Recuperation des logs du pipeline: {}", id);

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

    @GetMapping("/executions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'VIEWER')")
    public ResponseEntity<List<PipelineResponse>> getRecentExecutions() {
        log.info("Recuperation des executions recentes");

        List<PipelineExecution> executions = pipelineService.getRecentExecutions();
        List<PipelineResponse> responses = executions.stream()
                .map(exec -> PipelineResponse.fromExecution(exec, false))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/executions/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'VIEWER')")
    public ResponseEntity<List<PipelineResponse>> getExecutionsByStatus(@PathVariable String status) {
        log.info("Recuperation des executions avec statut: {}", status);

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

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<?> cancelPipeline(@PathVariable String id) {
        log.info("Demande d'annulation du pipeline: {}", id);

        boolean cancelled = pipelineService.cancelExecution(id);

        if (cancelled) {
            return ResponseEntity.ok(Map.of(
                    "message", "Pipeline annule avec succes",
                    "executionId", id
            ));
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Impossible d'annuler ce pipeline (n'existe pas ou n'est pas en cours)"));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Jonk CI/CD Engine",
                "version", "1.0.0"
        ));
    }
}
