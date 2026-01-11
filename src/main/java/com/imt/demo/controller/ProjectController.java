package com.imt.demo.controller;

import com.imt.demo.dto.PipelineResponse;
import com.imt.demo.dto.ProjectDto;
import com.imt.demo.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/project")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public List<ProjectDto> getProjects() {
        return projectService.getProjects();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ProjectDto getProject(@PathVariable String id) {
        return projectService.getProject(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto createProject(@RequestBody ProjectDto projectDto) {
        return projectService.createProject(projectDto);
    }


    @PostMapping("/{id}/pipeline")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<Map<String, String>> runPipeline(@PathVariable String id) {
        return projectService.runPipeline(id);
    }


    @GetMapping("/{id}/pipeline/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public List<PipelineResponse> getPipelineHistory(@PathVariable String id) {
        return projectService.getPipelineHistory(id);
    }


    @GetMapping("/{id}/pipeline/{pipelineId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public PipelineResponse getPipelineStatus(@PathVariable String id, @PathVariable String pipelineId) {
        return projectService.getPipelineStatus(pipelineId);
    }

}
