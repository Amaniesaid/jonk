package com.imt.demo.project.mapper;

import com.imt.demo.project.dto.ProjectDto;
import com.imt.demo.project.dto.ProjectSnippetDto;
import com.imt.demo.project.model.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProjectMapper {

    private final MachineMapper machineMapper;

    public Project toEntity(ProjectDto projectDto) {
        if (projectDto == null) {
            return null;
        }
        Project project = new Project();
        project.setId(projectDto.getId());
        project.setName(projectDto.getName());
        project.setGiturl(projectDto.getGiturl());
        if (projectDto.getMachines() != null) {
            project.setMachines(projectDto.getMachines().stream()
                    .map(machineMapper::toEntity)
                    .collect(Collectors.toList()));
        }
        project.setProjectType(projectDto.getProjectType());
        project.setContainerPort(projectDto.getContainerPort());
        project.setProdBranchName(projectDto.getProdBranchName());
        project.setDockerImageName(projectDto.getDockerImageName());
        return project;
    }

    public ProjectDto toDto(Project project) {
        if (project == null) {
            return null;
        }
        ProjectDto projectDto = new ProjectDto();
        projectDto.setId(project.getId());
        projectDto.setName(project.getName());
        projectDto.setGiturl(project.getGiturl());
        if (project.getMachines() != null) {
            projectDto.setMachines(project.getMachines().stream()
                    .map(machineMapper::toDto)
                    .collect(Collectors.toList()));
        }
        projectDto.setProjectType(project.getProjectType());
        projectDto.setContainerPort(project.getContainerPort());
        projectDto.setProdBranchName(project.getProdBranchName());
        projectDto.setDockerImageName(project.getDockerImageName());
        return projectDto;
    }

    public ProjectSnippetDto toSnippetDto(Project project) {
        if (project == null) {
            return null;
        }
        ProjectSnippetDto snippetDto = new ProjectSnippetDto();
        snippetDto.setId(project.getId());
        snippetDto.setName(project.getName());
        return snippetDto;
    }
}
