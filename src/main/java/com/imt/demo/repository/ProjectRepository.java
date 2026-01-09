package com.imt.demo.repository;

import com.imt.demo.dto.ProjectDto;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends MongoRepository<ProjectDto, String> {
}
