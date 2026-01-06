package com.imt.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Document MongoDB représentant l'exécution d'un pipeline
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "pipeline_executions")
public class PipelineExecution {

    @Id
    private String id;

    private String gitRepoUrl;
    private String gitBranch;
    private String commitHash;

    @Builder.Default
    private PipelineStatus status = PipelineStatus.PENDING;

    @Builder.Default
    private List<StepResult> steps = new ArrayList<>();

    private String errorMessage;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Long durationMs;

    private String triggeredBy;

    /**
     * Ajoute un résultat d'étape
     */
    public void addStepResult(StepResult stepResult) {
        this.steps.add(stepResult);
    }

    /**
     * Calcule la durée totale
     */
    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }
}

