package com.imt.demo.dto;

import com.imt.demo.model.PipelineExecution;
import com.imt.demo.model.PipelineStatus;
import com.imt.demo.model.StepResult;
import com.imt.demo.model.StepStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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

    private Integer totalSteps;
    private Integer successSteps;
    private Integer failedSteps;

    private List<StepResult> steps;

    public static PipelineResponse fromExecution(PipelineExecution execution, boolean includeSteps) {
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
                    .filter(s -> s.getStatus() == StepStatus.SUCCESS)
                    .count();
            long failed = execution.getSteps().stream()
                    .filter(s -> s.getStatus() == StepStatus.FAILED)
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
