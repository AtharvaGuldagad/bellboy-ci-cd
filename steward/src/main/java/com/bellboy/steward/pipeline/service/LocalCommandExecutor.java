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

    private final PipelineRunRepository repository;

    @Async
    @Override
    public void execute(PipelineRun run) {
        try {
            // STEP 2.2a: Update status to RUNNING
            updateStatus(run, PipelineStatus.RUNNING);
            log.info("Starting execution for Run ID: {}", run.getId());

            // STEP 2.1: Workspace Manager (Create Temp Directory)
            Path workspace = Files.createTempDirectory("bellboy-run-" + run.getId());
            log.info("Created workspace at: {}", workspace.toAbsolutePath());

            // STEP 2.2b: The LuggageLoaded Action (Git Clone)
            String cloneCommand = String.format("git clone %s .", run.getRepoURL());
            log.info("Executing: {}", cloneCommand);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cloneCommand);
            pb.directory(workspace.toFile());
            pb.redirectErrorStream(true); // Merges stderr into stdout

            Process process = pb.start();

            // Stream the terminal output to our Spring Boot console
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Run {}] {}", run.getId(), line);
                }
            }

            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("Git clone successful for Run ID: {}", run.getId());
                updateStatus(run, PipelineStatus.SUCCESS);
            } else {
                log.error("Git clone failed with exit code {} for Run ID: {}", exitCode, run.getId());
                updateStatus(run, PipelineStatus.FAILED);
            }

        } catch (Exception e) {
            // STEP 2.3: Error Handling (DoNotDisturb)
            log.error("Pipeline execution critically failed for Run ID: {}", run.getId(), e);
            updateStatus(run, PipelineStatus.FAILED);
        }
    }

    private void updateStatus(PipelineRun run, PipelineStatus status) {
        run.setStatus(status);
        if (status == PipelineStatus.SUCCESS || status == PipelineStatus.FAILED) {
            run.setEndTime(LocalDateTime.now());
        }
        repository.save(run);
    }
}
