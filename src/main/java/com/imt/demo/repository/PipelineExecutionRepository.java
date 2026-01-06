package com.imt.demo.repository;

import com.imt.demo.model.PipelineExecution;
import com.imt.demo.model.PipelineStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository pour la persistance des exécutions de pipeline dans MongoDB
 */
@Repository
public interface PipelineExecutionRepository extends MongoRepository<PipelineExecution, String> {

    /**
     * Trouve toutes les exécutions par statut
     */
    List<PipelineExecution> findByStatus(PipelineStatus status);

    /**
     * Trouve toutes les exécutions pour un repository Git
     */
    List<PipelineExecution> findByGitRepoUrl(String gitRepoUrl);

    /**
     * Trouve toutes les exécutions pour un repository et une branche
     */
    List<PipelineExecution> findByGitRepoUrlAndGitBranch(String gitRepoUrl, String gitBranch);

    /**
     * Trouve toutes les exécutions déclenchées par un utilisateur
     */
    List<PipelineExecution> findByTriggeredBy(String triggeredBy);

    /**
     * Trouve les exécutions récentes (dernières 24h)
     */
    List<PipelineExecution> findByStartTimeAfter(LocalDateTime since);

    /**
     * Trouve les exécutions récentes ordonnées par date (plus récentes en premier)
     */
    List<PipelineExecution> findTop10ByOrderByStartTimeDesc();
}
