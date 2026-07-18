package com.bellboy.steward.pipeline.service;

import com.bellboy.steward.pipeline.PipelineRun;

public interface PipelineExecutor {
    /**
     * Execs pipeline run asynchronously.
     * @param run The pipeline run entity containing repo and commit details.
     */
    void execute(PipelineRun run);
}
