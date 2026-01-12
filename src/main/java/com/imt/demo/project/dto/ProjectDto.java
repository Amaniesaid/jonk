package com.imt.demo.project.dto;

import com.imt.demo.project.model.ProjectType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ProjectDto {
    @NotNull
    private UUID id;

    @NotNull
    private String name;

    @NotNull
    private String giturl;

    @NotNull
    private List<MachineDto> machines;

    @NotNull
    private ProjectType projectType;

    @NotNull
    private int containerPort;
    
    @NotNull
    private String prodBranchName;

    @NotNull
    private String dockerImageName;
}
