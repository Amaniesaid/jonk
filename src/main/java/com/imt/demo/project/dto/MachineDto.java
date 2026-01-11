package com.imt.demo.project.dto;

import com.imt.demo.project.model.EnvironmentType;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MachineDto {
    private EnvironmentType environmentType;
    private String name;
    private UUID id;
    private int hostPort;
}
