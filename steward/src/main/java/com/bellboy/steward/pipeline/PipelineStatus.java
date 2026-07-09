package com.bellboy.steward.pipeline;

//Curr exec stage of pipeline run
public enum PipelineStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}
