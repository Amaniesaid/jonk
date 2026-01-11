package com.imt.demo.project.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;

import java.util.UUID;

@Getter
@Setter
public class Machine {
    private UUID id;
    private EnvironmentType environmentType;
    private String name;
    private int hostPort;
}
