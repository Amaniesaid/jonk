package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;

public interface PipelineStep {

    String getName();

    StepResult execute(PipelineContext context) throws Exception;

    void rollback(PipelineContext context) throws Exception;

    default boolean isCritical() {
        return true;
    }
}
