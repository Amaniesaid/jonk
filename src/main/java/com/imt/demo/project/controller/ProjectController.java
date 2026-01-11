package com.imt.demo.project.controller;

import com.imt.demo.pipeline.dto.PipelineResponse;
import com.imt.demo.project.dto.ProjectDto;
import com.imt.demo.project.dto.ProjectSnippetDto;
import com.imt.demo.project.service.ProjectService;
import jakarta.validation.Valid;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/project")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public List<ProjectSnippetDto> getProjects() {
        return projectService.getProjects();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ProjectSnippetDto getProject(@PathVariable UUID id) {
        return projectService.getProject(id);
    }

    @GetMapping("/{id}/sensitive")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ProjectDto getSensitiveProjectData(@PathVariable UUID id) {
        return projectService.getSensitiveProjectData(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto createProject(@Valid @RequestBody ProjectDto projectDto) {
        return projectService.createProject(projectDto);
    }

    @PostMapping("/{id}/pipeline")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<Map<String, String>> runPipeline(@PathVariable UUID id) {
        return projectService.runPipeline(id);
    }

    @GetMapping("/{id}/pipeline/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public List<PipelineResponse> getPipelineHistory(@PathVariable UUID id) {
        return projectService.getPipelineHistory(id);
    }

    @GetMapping("/{id}/pipeline/{pipelineId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public PipelineResponse getPipelineStatus(@PathVariable UUID id, @PathVariable String pipelineId) {
        return projectService.getPipelineStatus(pipelineId);
    }
}
