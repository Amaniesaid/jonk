package com.imt.demo.project.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;

import java.util.UUID;

@Getter
@Setter
@ToString
public class Machine {
    @Id
    private UUID id;
    private EnvironmentType environmentType;
    private String name;
    private int hostSshPort;
    private String hostSshUsername;
    private int deploymentPort;
    private String sshHost;
}
