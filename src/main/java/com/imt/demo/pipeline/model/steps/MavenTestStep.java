package com.imt.demo.pipeline.model.steps;

import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MavenTestStep extends AbstractPipelineStep {

    @Override
    public String getName() {
        return "Maven Test";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        String[] command = {
            "mvn", "test",
            "-B"
        };

        return executeCommand(command, context.getWorkspaceDir(), context.getEnvironmentVariables());
    }

    @Override
    public boolean isCritical() {
        return true;
    }
}
