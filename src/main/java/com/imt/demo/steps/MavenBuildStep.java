package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Étape 2: Build Maven
 */
@Slf4j
@Component
public class MavenBuildStep extends AbstractPipelineStep {

    @Override
    public String getName() {
        return "Maven Build";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        // Commande Maven: clean package (sans les tests, qui seront exécutés séparément)
        String[] command = {
            "mvn", "clean", "package",
            "-DskipTests",
            "-B"  // Mode batch (non-interactif)
        };

        StepResult result = executeCommand(command, context.getWorkspaceDir(), context.getEnvironmentVariables());

        // Si le build réussit, stocker le chemin de l'artifact
        if (result.getStatus() == com.imt.demo.model.StepStatus.SUCCESS) {
            String artifactPath = context.getWorkspaceDir() + "/target/*.jar";
            context.setArtifactPath(artifactPath);
            result.addLog(" Artifact généré: " + artifactPath);
        }

        return result;
    }
}

