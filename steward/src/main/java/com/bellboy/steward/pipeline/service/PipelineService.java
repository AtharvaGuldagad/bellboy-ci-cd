package com.bellboy.steward.pipeline.service;

import com.bellboy.steward.pipeline.PipelineRun;
import com.bellboy.steward.pipeline.PipelineRunRepository;
import com.bellboy.steward.pipeline.dto.WebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineRunRepository pipelineRunRepository;
    private final PipelineExecutor executor; 

    @Transactional
    public UUID registerNewRun(WebhookPayload payload) {
        String targetBranch = payload.getBranch() != null ? payload.getBranch() : "main";
        PipelineRun newRun = PipelineRun.builder()
                .repoURL(payload.getRepoUrl())
                .branch(targetBranch)
                .commitSHA(payload.getCommitSha())
                .build();
        PipelineRun savedRun = pipelineRunRepository.save(newRun);
        log.info("Registered new run with ID: {}", savedRun.getId());

        executor.execute(savedRun);

        return savedRun.getId();
    }
}