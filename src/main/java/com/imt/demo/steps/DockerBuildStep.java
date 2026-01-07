package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Étape 5: Build de l'image Docker
 */
@Slf4j
@Component
public class DockerBuildStep extends AbstractPipelineStep {

    @Override
    public String getName() {
        return "Docker Build";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        // Générer le tag de l'image
        String imageTag = context.getDockerImageTag() != null ?
                context.getDockerImageTag() :
                "latest-" + System.currentTimeMillis();

        String imageName = context.getDockerImageName();
        String fullImageName = imageName + ":" + imageTag;

        // Sauvegarder le tag pour les étapes suivantes
        context.setDockerImageTag(imageTag);

        // Commande Docker build
        String[] command = {
            "docker", "build",
            "-t", fullImageName,
            "."
        };

        StepResult result = executeCommand(command, context.getWorkspaceDir());

        if (result.getStatus() == com.imt.demo.model.StepStatus.SUCCESS) {
            result.addLog(" Image Docker créée: " + fullImageName);
        }

        return result;
    }
}

