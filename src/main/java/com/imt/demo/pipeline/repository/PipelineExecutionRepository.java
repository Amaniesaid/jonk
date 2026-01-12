package com.imt.demo.pipeline.repository;

import com.imt.demo.pipeline.model.PipelineExecution;
import com.imt.demo.pipeline.model.PipelineStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PipelineExecutionRepository extends MongoRepository<PipelineExecution, String> {

    List<PipelineExecution> findByStatus(PipelineStatus status);

    List<PipelineExecution> findByGitRepoUrl(String gitRepoUrl);

    List<PipelineExecution> findByGitRepoUrlAndStatusIn(String gitRepoUrl, List<PipelineStatus> statuses);

    List<PipelineExecution> findByGitRepoUrlAndGitBranch(String gitRepoUrl, String gitBranch);

    List<PipelineExecution> findByTriggeredBy(String triggeredBy);

    List<PipelineExecution> findByStartTimeAfter(LocalDateTime since);

    List<PipelineExecution> findTop10ByOrderByStartTimeDesc();
}
