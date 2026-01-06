package com.imt.demo.model;

/**
 * Statuts possibles d'un pipeline
 */
public enum PipelineStatus {
    PENDING,      // En attente
    RUNNING,      // En cours d'exécution
    SUCCESS,      // Succès
    FAILED,       // Échec
    ROLLING_BACK, // En cours de rollback
    ROLLED_BACK,  // Rollback effectué
    CANCELLED     // Annulé
}

