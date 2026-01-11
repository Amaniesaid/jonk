package com.imt.demo.model;

public enum PipelineStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    ROLLING_BACK,
    ROLLED_BACK,
    CANCELLED
}
