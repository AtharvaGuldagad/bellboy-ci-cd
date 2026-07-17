package com.bellboy.steward.pipeline.service;

import com.bellboy.steward.pipeline.PipelineRun;
import com.bellboy.steward.pipeline.PipelineRunRepository;
import com.bellboy.steward.pipeline.dto.WebhookPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineRunRepository pipelineRunRepository;

    /**
     * Registers a new pipeline run from an incoming webhook payload.
     * * @param payload The parsed JSON from the Git provider.
     * @return The UUID ofnewly created pipeline run.
     */
    @Transactional
    public UUID registerNewRun(WebhookPayload payload) {

        //branch not specified? default: main branch :P
        String targetBranch = payload.getBranch()!=null ? payload.getBranch() :"main";
        
        PipelineRun newRun = PipelineRun.builder()
                .repoURL(payload.getRepoUrl())
                .branch(targetBranch)
                .commitSHA(payload.getCommitSha())
                // status and datetime already filled up in the entity constructor
                .build();

        PipelineRun savedRun = pipelineRunRepository.save(newRun);
        
        // TODO: define trigger to start the pipeline run asynchronously (using kafka later on) and

        return savedRun.getId();
    }
}