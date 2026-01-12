package com.imt.demo.project.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Document(collection = "projects")
public class Project {
    @Id
    private UUID id;
    private String name;
    private String giturl;
    private ProjectType projectType;
    private List<Machine> machines;
    private int containerPort;
    private String prodBranchName;
    private String dockerImageName;
}
