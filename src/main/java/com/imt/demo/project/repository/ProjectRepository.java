package com.imt.demo.project.repository;

import com.imt.demo.project.model.Project;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends MongoRepository<Project, String> {
}
