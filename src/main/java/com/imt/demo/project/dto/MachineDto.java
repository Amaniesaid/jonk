package com.imt.demo.project.dto;

import com.imt.demo.project.model.EnvironmentType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MachineDto {
    @NotNull
    private UUID id;

    @NotNull
    private EnvironmentType environmentType;

    @NotNull
    private String name;

    @NotNull
    private int hostPort;
}
