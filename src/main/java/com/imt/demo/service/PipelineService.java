package com.imt.demo.service;

import com.imt.demo.engine.PipelineEngine;
import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.PipelineExecution;
import com.imt.demo.model.PipelineStatus;
import com.imt.demo.model.StepResult;
import com.imt.demo.repository.PipelineExecutionRepository;
import com.imt.demo.steps.PipelineStep;
import com.imt.demo.steps.GitCloneStep;
import com.imt.demo.steps.MavenBuildStep;
import com.imt.demo.steps.MavenTestStep;
import com.imt.demo.steps.SonarQubeStep;
import com.imt.demo.steps.DockerBuildStep;
import com.imt.demo.steps.DockerScanStep;
import com.imt.demo.steps.DockerDeployStep;
import com.imt.demo.steps.HealthCheckStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineEngine pipelineEngine;
    private final PipelineExecutionRepository executionRepository;

    private final GitCloneStep gitCloneStep;
    private final MavenBuildStep mavenBuildStep;
    private final MavenTestStep mavenTestStep;
    private final SonarQubeStep sonarQubeStep;
    private final DockerBuildStep dockerBuildStep;
    private final DockerScanStep dockerScanStep;
    private final DockerDeployStep dockerDeployStep;
    private final HealthCheckStep healthCheckStep;

    @Async("pipelineExecutor")
    public CompletableFuture<String> runPipelineAsync(PipelineContext context) {
        String executionId = UUID.randomUUID().toString();
        context.setExecutionId(executionId);
        context.setPipelineId(executionId);

        log.info("Demarrage asynchrone du pipeline: {}", executionId);

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

        try {
            pipelineEngine.validateContext(context);
        } catch (IllegalArgumentException e) {
            log.error("Validation du contexte echouee: {}", e.getMessage());
            execution.setStatus(PipelineStatus.FAILED);
            execution.setErrorMessage("Validation echouee: " + e.getMessage());
            execution.setEndTime(LocalDateTime.now());
            execution.calculateDuration();
            executionRepository.save(execution);
            return CompletableFuture.completedFuture(executionId);
        }

        List<PipelineStep> steps = buildPipelineSteps(context);

        try {
            PipelineExecution result = pipelineEngine.executePipeline(context, steps);

            execution.setStatus(result.getStatus());
            execution.setSteps(result.getSteps());
            execution.setErrorMessage(result.getErrorMessage());
            execution.setEndTime(result.getEndTime());
            execution.setCommitHash(context.getCommitHash());
            execution.calculateDuration();

            executionRepository.save(execution);

            log.info("Pipeline termine: {} - Statut: {}", executionId, execution.getStatus());

        } catch (Exception e) {
            log.error("Erreur lors de l'execution du pipeline: {}", executionId, e);
            execution.setStatus(PipelineStatus.FAILED);
            execution.setErrorMessage("Exception: " + e.getMessage());
            execution.setEndTime(LocalDateTime.now());
            execution.calculateDuration();
            executionRepository.save(execution);
        }

        return CompletableFuture.completedFuture(executionId);
    }

    public PipelineExecution runPipelineSync(PipelineContext context) {
        String executionId = UUID.randomUUID().toString();
        context.setExecutionId(executionId);
        context.setPipelineId(executionId);

        pipelineEngine.validateContext(context);

        List<PipelineStep> steps = buildPipelineSteps(context);

        PipelineExecution execution = pipelineEngine.executePipeline(context, steps);
        execution.setId(executionId);

        return executionRepository.save(execution);
    }

    private List<PipelineStep> buildPipelineSteps(PipelineContext context) {
        List<PipelineStep> steps = new ArrayList<>();

        steps.add(gitCloneStep);
        steps.add(mavenBuildStep);
        steps.add(mavenTestStep);

        if (Boolean.TRUE.equals(context.getSonarEnabled())) {
            steps.add(sonarQubeStep);
        }

        steps.add(dockerBuildStep);
        steps.add(dockerScanStep);

        if (context.getDeploymentPort() != null) {
            steps.add(dockerDeployStep);
            steps.add(healthCheckStep);
        }

        log.info("Pipeline configure avec {} etapes", steps.size());
        return steps;
    }

    public Optional<PipelineExecution> getExecution(String executionId) {
        return executionRepository.findById(executionId);
    }

    public List<PipelineExecution> getRecentExecutions() {
        return executionRepository.findTop10ByOrderByStartTimeDesc();
    }

    public List<PipelineExecution> getExecutionsByStatus(PipelineStatus status) {
        return executionRepository.findByStatus(status);
    }

    public List<String> getExecutionLogs(String executionId) {
        Optional<PipelineExecution> execution = executionRepository.findById(executionId);

        if (execution.isEmpty()) {
            return List.of("Execution non trouvee");
        }

        List<String> allLogs = new ArrayList<>();
        allLogs.add("===============================================================");
        allLogs.add("LOGS DU PIPELINE: " + executionId);
        allLogs.add("===============================================================");
        allLogs.add("");

        PipelineExecution exec = execution.get();
        allLogs.add("Repository: " + exec.getGitRepoUrl());
        allLogs.add("Branche: " + exec.getGitBranch());
        allLogs.add("Commit: " + (exec.getCommitHash() != null ? exec.getCommitHash() : "N/A"));
        allLogs.add("Declenche par: " + exec.getTriggeredBy());
        allLogs.add("Statut: " + exec.getStatus());
        allLogs.add("Duree: " + (exec.getDurationMs() != null ? exec.getDurationMs() + "ms" : "N/A"));
        allLogs.add("");

        if (exec.getSteps() != null) {
            for (StepResult step : exec.getSteps()) {
                allLogs.add("---------------------------------------------------------------");
                allLogs.add("ETAPE: " + step.getStepName());
                allLogs.add("Statut: " + step.getStatus());
                allLogs.add("Duree: " + (step.getDurationMs() != null ? step.getDurationMs() + "ms" : "N/A"));
                allLogs.add("---------------------------------------------------------------");

                if (step.getLogs() != null) {
                    allLogs.addAll(step.getLogs());
                }

                if (step.getErrorMessage() != null) {
                    allLogs.add("ERREUR: " + step.getErrorMessage());
                }

                allLogs.add("");
            }
        }

        allLogs.add("===============================================================");
        allLogs.add("FIN DES LOGS");
        allLogs.add("===============================================================");

        return allLogs;
    }

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
            log.warn("Pipeline annule: {}", executionId);
            return true;
        }

        return false;
    }
}
