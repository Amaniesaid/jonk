package com.imt.demo.project.service;

import com.imt.demo.pipeline.dto.PipelineResponse;
import com.imt.demo.project.dto.ProjectDto;
import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.PipelineExecution;
import com.imt.demo.pipeline.service.PipelineService;
import com.imt.demo.pipeline.repository.PipelineExecutionRepository;
import com.imt.demo.project.repository.ProjectRepository;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {
    private final PipelineExecutionRepository pipelineExecutionRepository;
    private final PipelineService pipelineService;
    private final ProjectRepository projectRepository;

    public List<ProjectDto> getProjects() {
        return projectRepository.findAll();
    }

    public ProjectDto getProject(String id) {
        return projectRepository.findById(id).orElseThrow(() ->
            new NotFoundException("No such project " + id)
        );
    }

    public ProjectDto createProject(ProjectDto projectDto) {
        return projectRepository.save(projectDto);
    }

    public ResponseEntity<Map<String, String>> runPipeline(String id) {
        ProjectDto project = projectRepository.findById(id).orElseThrow(() -> 
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

    public List<PipelineResponse> getPipelineHistory(String id) {
        ProjectDto project = projectRepository.findById(id).orElseThrow(() -> 
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
