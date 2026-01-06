package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Étape 3: Exécution des tests unitaires Maven
 */
@Slf4j
@Component
public class MavenTestStep extends AbstractPipelineStep {

    @Override
    public String getName() {
        return "Maven Test";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        // Commande Maven: exécution des tests
        String[] command = {
            "mvn", "test",
            "-B"  // Mode batch (non-interactif)
        };

        return executeCommand(command, context.getWorkspaceDir(), context.getEnvironmentVariables());
    }

    @Override
    public boolean isCritical() {
        // Les tests sont critiques - le pipeline s'arrête si les tests échouent
        return true;
    }
}

