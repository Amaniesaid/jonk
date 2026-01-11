package com.imt.demo.pipeline.model;

public enum PipelineStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    ROLLING_BACK,
    ROLLED_BACK,
    CANCELLED
}
