package com.imt.demo.pipeline.model.steps;

import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.StepResult;
import com.imt.demo.pipeline.model.StepStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MavenBuildStep extends AbstractPipelineStep {

    @Override
    public String getName() {
        return "Maven Build";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        String[] command = {
            "mvn.cmd", "clean", "package",
            "-DskipTests",
            "-B"
        };

        StepResult result = executeCommand(command, context.getWorkspaceDir(), context.getEnvironmentVariables());

        if (result.getStatus() == StepStatus.SUCCESS) {
            String artifactPath = context.getWorkspaceDir() + "/target/*.jar";
            context.setArtifactPath(artifactPath);
            result.addLog("Artifact genere: " + artifactPath);
        }

        return result;
    }
}
