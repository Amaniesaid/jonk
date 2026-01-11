package com.imt.demo.repository;

import com.imt.demo.model.PipelineExecution;
import com.imt.demo.model.PipelineStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PipelineExecutionRepository extends MongoRepository<PipelineExecution, String> {

    List<PipelineExecution> findByStatus(PipelineStatus status);

    List<PipelineExecution> findByGitRepoUrl(String gitRepoUrl);

    List<PipelineExecution> findByGitRepoUrlAndGitBranch(String gitRepoUrl, String gitBranch);

    List<PipelineExecution> findByTriggeredBy(String triggeredBy);

    List<PipelineExecution> findByStartTimeAfter(LocalDateTime since);

    List<PipelineExecution> findTop10ByOrderByStartTimeDesc();
}
