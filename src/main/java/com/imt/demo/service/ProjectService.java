package com.imt.demo.service;

import com.imt.demo.controller.PipelineController;
import com.imt.demo.dto.PipelineRequest;
import com.imt.demo.dto.PipelineResponse;
import com.imt.demo.dto.ProjectDto;
import com.imt.demo.model.PipelineExecution;
import com.imt.demo.repository.PipelineExecutionRepository;
import com.imt.demo.repository.ProjectRepository;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {
    private final PipelineExecutionRepository pipelineExecutionRepository;

    private final PipelineController pipelineController;

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
        ProjectDto project = projectRepository.findById(id).orElseThrow(() -> new NotFoundException("No such project " + id));

        PipelineRequest request = new PipelineRequest();
        request.setGitUrl(project.getGiturl());
        request.setBranch("main");

        request.setDockerImageName("tuto-web-service");
        request.setDeploymentHost("localhost");
        request.setDeploymentUser("debian");
        request.setDockerPort("8089");
        request.setDeploymentPort("2288");


        return pipelineController.runPipeline(request);
    }

    public List<PipelineResponse> getPipelineHistory(String id) {
        ProjectDto project = projectRepository.findById(id).orElseThrow(() -> new NotFoundException("No such project " + id));


        List<PipelineExecution> executions =  pipelineExecutionRepository.findByGitRepoUrl(project.getGiturl());
        List<PipelineResponse> responses = executions.stream()
                .map(exec -> PipelineResponse.fromExecution(exec, false))
                .collect(Collectors.toList());

        return responses;

    }

    public PipelineResponse getPipelineStatus(String pipelineId) {
        PipelineExecution exec =  pipelineExecutionRepository.findById(pipelineId).orElseThrow(() -> new NotFoundException("No such pipeline " + pipelineId));
        PipelineResponse response = PipelineResponse.fromExecution(exec, true);

        return response;

    }
}
