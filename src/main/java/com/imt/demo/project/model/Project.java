package com.imt.demo.project.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Getter
@Setter
@Document(collection = "projects")
public class Project {
    @Id
    private String id;
    private String name;
    private String giturl;
    private ProjectType projectType;
    private List<Machine> machines;
    private int containerPort;
}
