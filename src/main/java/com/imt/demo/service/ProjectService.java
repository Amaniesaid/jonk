package com.imt.demo.service;

import com.imt.demo.dto.ProjectDto;
import com.imt.demo.repository.ProjectRepository;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {
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
}
