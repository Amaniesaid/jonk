package com.imt.demo.project.service;

import com.imt.demo.pipeline.dto.PipelineResponse;
import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.PipelineExecution;
import com.imt.demo.pipeline.model.PipelineStatus;
import com.imt.demo.pipeline.repository.PipelineExecutionRepository;
import com.imt.demo.pipeline.service.PipelineService;
import com.imt.demo.project.dto.ProjectDto;
import com.imt.demo.project.dto.ProjectSnippetDto;
import com.imt.demo.project.mapper.ProjectMapper;
import com.imt.demo.project.model.EnvironmentType;
import com.imt.demo.project.model.Machine;
import com.imt.demo.project.model.Project;
import com.imt.demo.project.repository.ProjectRepository;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {
    private static final String NO_SUCH_PROJECT = "No such project ";
    private final PipelineExecutionRepository pipelineExecutionRepository;
    private final PipelineService pipelineService;
    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;

    public List<ProjectSnippetDto> getProjects() {
        return projectRepository.findAll().stream()
                .map(projectMapper::toSnippetDto)
                .toList();
    }

    public ProjectDto getProject(UUID id) {
        Project project = projectRepository.findById(id).orElseThrow(() ->
                new NotFoundException(NO_SUCH_PROJECT + id)
        );
        return projectMapper.toDto(project);
    }

    public ProjectDto createProject(ProjectDto projectDto) {
        Project project = projectMapper.toEntity(projectDto);
        Project savedProject = projectRepository.save(project);
        return projectMapper.toDto(savedProject);
    }

    public ResponseEntity<Map<String, String>> runPipeline(UUID id, String userTrigger) {
        Project project = projectRepository.findById(id).orElseThrow(() ->
                new NotFoundException(NO_SUCH_PROJECT + id)
        );

        List<PipelineExecution> runningPipelines = pipelineExecutionRepository.findByGitRepoUrlAndStatusIn(
                project.getGiturl(),
                Arrays.asList(PipelineStatus.PENDING, PipelineStatus.RUNNING)
        );

        if (!runningPipelines.isEmpty()) {
            throw new BadRequestException("A pipeline is already running for this project.");
        }

        Machine prodMachine = project.getMachines().getFirst();

        if (prodMachine == null || prodMachine.getEnvironmentType() != EnvironmentType.PROD) {
            throw new InternalServerErrorException("Project has no prod machine: first machine found was {}" + prodMachine);
        }

        PipelineContext context = PipelineContext.builder()
                .gitUrl(project.getGiturl())
                .branch(project.getProdBranchName())
                .dockerImageName(project.getDockerImageName())
                .deploymentHost(prodMachine.getSshHost())
                .deploymentUser(prodMachine.getHostSshUsername())
                .deploymentPort(prodMachine.getHostSshPort())
                .applicationPort(project.getContainerPort())
                .applicationHostPort(prodMachine.getDeploymentPort())
                .containerName(project.getDockerImageName() + project.getId().toString())
                .environmentVariables(new HashMap<>())
                .sonarEnabled(true)
                .triggeredBy(userTrigger)
                .build();

        CompletableFuture<String> executionFuture = pipelineService.runPipelineAsync(context);

        executionFuture.handle((executionId, ex) -> {
            if (ex != null) {
                log.error("Erreur lors du lancement du pipeline", ex);
            } else {
                log.info("Pipeline lance avec succes: {}", executionId);
            }
            return null;
        });

        Map<String, String> response = new HashMap<>();
        response.put("message", "Pipeline demarre avec succes");
        response.put("status", "PENDING");

        return ResponseEntity.accepted().body(response);
    }

    public List<PipelineResponse> getPipelineHistory(UUID id) {
        Project project = projectRepository.findById(id).orElseThrow(() ->
                new NotFoundException(NO_SUCH_PROJECT + id)
        );

        List<PipelineExecution> executions = pipelineExecutionRepository.findByGitRepoUrl(project.getGiturl());
        return executions.stream()
                .map(exec -> PipelineResponse.fromExecution(exec, false))
                .toList();
    }

    public PipelineResponse getPipelineStatus(String pipelineId) {
        PipelineExecution exec = pipelineExecutionRepository.findById(pipelineId).orElseThrow(() ->
                new NotFoundException("No such pipeline " + pipelineId)
        );
        return PipelineResponse.fromExecution(exec, true);
    }
}
