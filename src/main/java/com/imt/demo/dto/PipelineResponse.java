package com.imt.demo.dto;

import com.imt.demo.model.PipelineStatus;
import com.imt.demo.model.StepResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO pour la réponse contenant les informations d'un pipeline
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineResponse {

    private String executionId;
    private String gitRepoUrl;
    private String gitBranch;
    private String commitHash;
    private PipelineStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String errorMessage;
    private String triggeredBy;

    // Résumé des étapes
    private Integer totalSteps;
    private Integer successSteps;
    private Integer failedSteps;

    // Détails des étapes (optionnel selon l'endpoint)
    private List<StepResult> steps;

    /**
     * Construit une réponse simple sans les détails des étapes
     */
    public static PipelineResponse fromExecution(com.imt.demo.model.PipelineExecution execution, boolean includeSteps) {
        PipelineResponseBuilder builder = PipelineResponse.builder()
                .executionId(execution.getId())
                .gitRepoUrl(execution.getGitRepoUrl())
                .gitBranch(execution.getGitBranch())
                .commitHash(execution.getCommitHash())
                .status(execution.getStatus())
                .startTime(execution.getStartTime())
                .endTime(execution.getEndTime())
                .durationMs(execution.getDurationMs())
                .errorMessage(execution.getErrorMessage())
                .triggeredBy(execution.getTriggeredBy());

        if (execution.getSteps() != null) {
            long success = execution.getSteps().stream()
                    .filter(s -> s.getStatus() == com.imt.demo.model.StepStatus.SUCCESS)
                    .count();
            long failed = execution.getSteps().stream()
                    .filter(s -> s.getStatus() == com.imt.demo.model.StepStatus.FAILED)
                    .count();

            builder.totalSteps(execution.getSteps().size())
                    .successSteps((int) success)
                    .failedSteps((int) failed);

            if (includeSteps) {
                builder.steps(execution.getSteps());
            }
        }

        return builder.build();
    }
}
