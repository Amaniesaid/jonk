package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;

/**
 * Interface définissant le contrat d'une étape de pipeline.
 * Chaque étape doit implémenter cette interface.
 */
public interface PipelineStep {

    /**
     * Nom de l'étape (utilisé pour les logs)
     */
    String getName();

    /**
     * Exécute l'étape
     * @param context Contexte du pipeline
     * @return Résultat de l'exécution
     * @throws Exception en cas d'erreur
     */
    StepResult execute(PipelineContext context) throws Exception;

    /**
     * Effectue le rollback de l'étape
     * @param context Contexte du pipeline
     * @throws Exception en cas d'erreur
     */
    void rollback(PipelineContext context) throws Exception;

    /**
     * Indique si cette étape est critique (déclenchera un rollback en cas d'échec)
     */
    default boolean isCritical() {
        return true;
    }
}

