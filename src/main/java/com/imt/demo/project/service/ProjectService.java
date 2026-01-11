package com.imt.demo.project.service;

import com.imt.demo.pipeline.dto.PipelineResponse;
import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.PipelineExecution;
import com.imt.demo.pipeline.repository.PipelineExecutionRepository;
import com.imt.demo.pipeline.service.PipelineService;
import com.imt.demo.project.dto.ProjectDto;
import com.imt.demo.project.dto.ProjectSnippetDto;
import com.imt.demo.project.mapper.ProjectMapper;
import com.imt.demo.project.model.Project;
import com.imt.demo.project.repository.ProjectRepository;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {
    private final PipelineExecutionRepository pipelineExecutionRepository;
    private final PipelineService pipelineService;
    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;

    public List<ProjectSnippetDto> getProjects() {
        return projectRepository.findAll().stream()
                .map(projectMapper::toSnippetDto)
                .collect(Collectors.toList());
    }

    public ProjectSnippetDto getProject(UUID id) {
        Project project = projectRepository.findById(id).orElseThrow(() ->
                new NotFoundException("No such project " + id)
        );
        return projectMapper.toSnippetDto(project);
    }

    public ProjectDto getSensitiveProjectData(UUID id) {
        Project project = projectRepository.findById(id).orElseThrow(() ->
                new NotFoundException("No such project " + id)
        );
        return projectMapper.toDto(project);
    }

    public ProjectDto createProject(ProjectDto projectDto) {
        Project project = projectMapper.toEntity(projectDto);
        Project savedProject = projectRepository.save(project);
        return projectMapper.toDto(savedProject);
    }

    public ResponseEntity<Map<String, String>> runPipeline(UUID id) {
        Project project = projectRepository.findById(id).orElseThrow(() ->
                new NotFoundException("No such project " + id)
        );

        PipelineContext context = PipelineContext.builder()
                .gitUrl(project.getGiturl())
                .branch("main")
                .dockerImageName("tuto-web-service")
                .deploymentHost("localhost")
                .deploymentUser("debian")
                .deploymentPort("2288")
                .environmentVariables(new HashMap<>())
                .triggeredBy("user")
                .build();

        try {
            String executionId = pipelineService.runPipelineAsync(context).join();

            log.info("Pipeline lance avec succes: {}", executionId);

            Map<String, String> response = new HashMap<>();
            response.put("executionId", executionId);
            response.put("message", "Pipeline demarre avec succes");
            response.put("status", "RUNNING");

            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("Erreur lors du lancement du pipeline", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Erreur lors du lancement: " + e.getMessage()));
        }
    }

    public List<PipelineResponse> getPipelineHistory(UUID id) {
        Project project = projectRepository.findById(id).orElseThrow(() ->
                new NotFoundException("No such project " + id)
        );

        List<PipelineExecution> executions = pipelineExecutionRepository.findByGitRepoUrl(project.getGiturl());
        return executions.stream()
                .map(exec -> PipelineResponse.fromExecution(exec, false))
                .collect(Collectors.toList());
    }

    public PipelineResponse getPipelineStatus(String pipelineId) {
        PipelineExecution exec = pipelineExecutionRepository.findById(pipelineId).orElseThrow(() ->
                new NotFoundException("No such pipeline " + pipelineId)
        );
        return PipelineResponse.fromExecution(exec, true);
    }
}
