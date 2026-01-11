package com.imt.demo.project.dto;

import com.imt.demo.project.model.ProjectType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ProjectDto {
    private UUID id;
    private String name;
    private String giturl;
    private List<MachineDto> machines;
    private ProjectType projectType;
    private int containerPort;
}
