package com.bellboy.steward.pipeline.service;

import com.bellboy.steward.pipeline.PipelineRun;
import com.bellboy.steward.pipeline.PipelineRunRepository;
import com.bellboy.steward.pipeline.PipelineStatus;
import com.bellboy.steward.pipeline.dto.config.BellboyConfig;
import com.bellboy.steward.pipeline.dto.config.TaskDef;

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
import java.util.Map;
import java.util.UUID;

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
                    log.info("Preparing to execute pipeline: {}", config.getPipeline().getName());

                    // before u go in loop check if stages are defined
                    if (config.getPipeline().getStages() == null || config.getPipeline().getStages().isEmpty()) {
                        log.warn("No stages defined in .bellboy.yml. Nothing to execute.");
                    } else {

                        // follow strict order of stages defined by user in the config file
                        for (String currentStage : config.getPipeline().getStages()) {
                            log.info("=== Starting Stage: {} ===", currentStage);

                            // map all tasks inside curr stage
                            if (config.getPipeline().getTasks() != null) {
                                for (Map.Entry<String, TaskDef> taskEntry : config.getPipeline().getTasks().entrySet()) {
                                    String taskName = taskEntry.getKey();
                                    TaskDef taskDetails = taskEntry.getValue();

                                    // filter tasks: edge case: if task stage is not defined, skip it
                                    if (taskDetails.getStage() == null) {
                                        log.warn("Task '{}' does not belong to any stage. Skipping.", taskName);
                                        continue;
                                    }
                                    if (currentStage.equals(taskDetails.getStage())) {
                                        log.info("-> Preparing Task: {}", taskName);

                                        // make sure commands are defined for the task otherwise we skip ofc
                                        if (taskDetails.getCommands() != null) {
                                            for (String cmd : taskDetails.getCommands()) {

                                                // Send it (to the OS) store the res
                                                boolean isSuccess = executeShellCommand(cmd, workspace, run.getId());

                                                if (!isSuccess) {
                                                    // If a command fails, the whole pipeline must halt.
                                                    throw new RuntimeException(
                                                            "Pipeline halted due to failed command: " + cmd);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
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
    private boolean executeShellCommand(String command, Path workspace, UUID runId) {
    log.info("[Run {}] Executing native command: {}", runId, command);
    
    try {
        // THE WRAPPERRRR, abstracting the OS command exec
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        // working dir ofc the cloned repo, not the temp dir
        pb.directory(workspace.toFile());
        
        // LEARN: how to stream logs and errors from the process to our log

        Process process = pb.start(); 
        
        // rn we jus pretend we succeeded, validate later with OS exit code
        return true; 
        
    } catch (Exception e) {
        log.error("[Run {}] OS Execution failed for command: {}", runId, command, e);
        return false;
    }
}
}

