package com.imt.demo.pipeline.model.steps;

import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.StepResult;

public interface PipelineStep {

    String getName();

    StepResult getInitialStepResult();

    StepResult execute(PipelineContext context) throws Exception;

    void rollback(PipelineContext context) throws Exception;

    default boolean isCritical() {
        return true;
    }
}
