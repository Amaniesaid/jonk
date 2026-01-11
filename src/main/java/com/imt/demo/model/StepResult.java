package com.imt.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {

    private String stepName;

    @Builder.Default
    private StepStatus status = StepStatus.PENDING;

    @Builder.Default
    private List<String> logs = new ArrayList<>();

    private String errorMessage;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Long durationMs;

    public void addLog(String log) {
        this.logs.add(log);
    }

    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            this.durationMs = Duration.between(startTime, endTime).toMillis();
        }
    }
}
