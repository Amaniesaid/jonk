package com.imt.demo.model;

/**
 * Statuts possibles d'une étape du pipeline
 */
public enum StepStatus {
    PENDING,      // En attente
    RUNNING,      // En cours d'exécution
    SUCCESS,      // Succès
    FAILED,       // Échec
    SKIPPED,      // Ignorée
    ROLLED_BACK   // Rollback effectué
}

