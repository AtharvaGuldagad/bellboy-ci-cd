package com.bellboy.steward.pipeline.service;

import com.bellboy.steward.pipeline.PipelineRun;
import com.bellboy.steward.pipeline.PipelineRunRepository;
import com.bellboy.steward.pipeline.PipelineStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalCommandExecutor implements PipelineExecutor {

    private final PipelineRunRepository pipelineRunRepository;

    @Async
    @Override
    public void execute(PipelineRun run) {
        log.info("Started execution for Pipeline Run: {}", run.getId());

        // Update status to RUNNING
        pipelineRunRepository.findById(run.getId()).ifPresent(freshRun -> {
            freshRun.setStatus(PipelineStatus.RUNNING);
            pipelineRunRepository.save(freshRun);
        });

        try {
            // Create a isolated workspace for this pipeline run
            Path workspace = Files.createTempDirectory("bellboy-workspace-" + run.getId());
            log.info("Provisioned local workspace at: {}", workspace.toAbsolutePath());

            // Config the OS process to run Git clone
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "git", "clone", run.getRepoURL(), "."
            );
            
            // Set the working dir to the temp folder
            processBuilder.directory(workspace.toFile());
            
            // Merge Std Error and Std Out to get a single log stream
            processBuilder.redirectErrorStream(true);

            // Start the OS process
            Process process = processBuilder.start();

            // Stream logs from the terminal
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // MVP: Just print to console. Future: Stream to Kafka
                    log.info("[RUN-{}] {}", run.getId().toString().substring(0,8), line);
                }
            }

            // Wait for the process to finish and get the OS exit code
            int exitCode = process.waitFor();
            log.info("Process finished with Exit Code: {}", exitCode);

            // Update status based on Linux standard (0 = Success, anything else = Failure)
            PipelineStatus finalStatus = (exitCode == 0) ? PipelineStatus.SUCCESS : PipelineStatus.FAILED;
            
            pipelineRunRepository.findById(run.getId()).ifPresent(freshRun -> {
                freshRun.setStatus(finalStatus);
                freshRun.setEndTime(LocalDateTime.now());
                pipelineRunRepository.save(freshRun);
                log.info("Pipeline Run {} concluded with status: {}", freshRun.getId(), freshRun.getStatus());
            });

        } catch (Exception e) {
            log.error("Catastrophic system failure during pipeline execution.", e);
            
            pipelineRunRepository.findById(run.getId()).ifPresent(freshRun -> {
                freshRun.setStatus(PipelineStatus.FAILED);
                freshRun.setEndTime(LocalDateTime.now());
                pipelineRunRepository.save(freshRun);
            });
        }
    }
}