package com.bellboy.steward.pipeline.service;

import com.bellboy.steward.pipeline.PipelineRun;
import com.bellboy.steward.pipeline.PipelineRunRepository;
import com.bellboy.steward.pipeline.PipelineStatus;
import com.bellboy.steward.pipeline.dto.config.BellboyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalCommandExecutor implements PipelineExecutor {

    private final PipelineRunRepository repository;
    private final PipelineConfigParser configParser;

    @Async
    @Override
    public void execute(PipelineRun run) {
        try {
            updateStatus(run.getId(), PipelineStatus.RUNNING);
            log.info("Starting execution for Run ID: {}", run.getId());

            Path workspace = Files.createTempDirectory("bellboy-run-" + run.getId());
            log.info("Created workspace at: {}", workspace.toAbsolutePath());

            String cloneCommand = String.format("git clone %s .", run.getRepoURL());
            log.info("Executing: {}", cloneCommand);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cloneCommand);
            pb.directory(workspace.toFile());
            pb.redirectErrorStream(true); 

            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Run {}] {}", run.getId().toString().substring(0,8), line);
                }
            }
            int exitCode = process.waitFor();          
            if (exitCode == 0) {
                log.info("Git clone successful for Run ID: {}", run.getId());
                try {
                    BellboyConfig config = configParser.parseConfiguration(workspace);
                    log.info("Successfully parsed .bellboy.yml. Pipeline Name: {}", config.getPipeline().getName());
                    updateStatus(run.getId(), PipelineStatus.SUCCESS); 
                    
                } catch (Exception e) {
                    log.error("Failed to extract configuration: {}", e.getMessage());
                    updateStatus(run.getId(), PipelineStatus.FAILED);
                }
                
            } else {
                log.error("Git clone failed with exit code {} for Run ID: {}", exitCode, run.getId());
                updateStatus(run.getId(), PipelineStatus.FAILED);
            }

        } catch (Exception e) {
            log.error("Pipeline execution critically failed for Run ID: {}", run.getId(), e);
            updateStatus(run.getId(), PipelineStatus.FAILED);
        }
    }
 // gg
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(java.util.UUID runId, PipelineStatus status) {
        repository.findById(runId).ifPresent(freshRun -> {
            freshRun.setStatus(status);
            if (status == PipelineStatus.SUCCESS || status == PipelineStatus.FAILED) {
                freshRun.setEndTime(LocalDateTime.now());
            }
            repository.save(freshRun);
        });
    }
}