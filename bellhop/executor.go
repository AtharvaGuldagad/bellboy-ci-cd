package main

import (
	"bufio"
	"context"
	"fmt"

	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/api/types/image"
	"github.com/docker/docker/client"

	"path/filepath"

	"gopkg.in/yaml.v3"
)

// DATA MODELS
type BellboyConfig struct {
	Pipeline PipelineDef `yaml:"pipeline"`
}

type PipelineDef struct {
	Name   string             `yaml:"name"`
	Runner RunnerDef          `yaml:"runner"`
	Stages []string           `yaml:"stages"`
	Tasks  map[string]TaskDef `yaml:"tasks"`
	Artifacts []string        `yaml:"artifacts"`
}

type RunnerDef struct {
	Image string `yaml:"image"`
}

type TaskDef struct {
	Stage    string   `yaml:"stage"`
	Commands []string `yaml:"commands"`
}

// PARSER
func parseConfiguration(filePath string) (*BellboyConfig, error) {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return nil, err
	}

	var config BellboyConfig
	err = yaml.Unmarshal(data, &config)
	if err != nil {
		return nil, err
	}

	return &config, nil
}

// runs shell commands in a container (uses the image u specify in yml)
func executeDockerCommand(runID string, imageName string, command string, workspace string) bool {
	ctx := context.Background()
	PublishEvent(runID, "LOG", fmt.Sprintf("Initializing isolated container from image: %s", imageName))

	// Connect to the host's Docker Engine
	cli, err := client.NewClientWithOpts(client.FromEnv, client.WithAPIVersionNegotiation())
	if err != nil {
		PublishEvent(runID, "ERROR", fmt.Sprintf("Docker Engine connection failed: %v", err))
		return false
	}
	defer cli.Close()

	// Pull the Image (if not already cached locally)
	reader, err := cli.ImagePull(ctx, imageName, image.PullOptions{})
	if err != nil {
		PublishEvent(runID, "ERROR", fmt.Sprintf("Failed to pull image '%s': %v", imageName, err))
		return false
	}
	reader.Close()

	// Create the Container & Mount the Workspace
	// ts binds the host's temporary workspace folder to "/workspace" inside the container
	resp, err := cli.ContainerCreate(ctx, &container.Config{
		Image:      imageName,
		Cmd:        []string{"bash", "-c", command},
		Tty:        true, // Merges Stdout and Stderr so we can stream it cleanly
		WorkingDir: "/workspace",
	}, &container.HostConfig{
		Binds: []string{workspace + ":/workspace"},
	}, nil, nil, "")
	
	if err != nil {
		PublishEvent(runID, "ERROR", fmt.Sprintf("Failed to provision container: %v", err))
		return false
	}

	// clean up.. container is destroyed when we finish
	defer cli.ContainerRemove(ctx, resp.ID, container.RemoveOptions{Force: true})

	// Start the Execution
	if err := cli.ContainerStart(ctx, resp.ID, container.StartOptions{}); err != nil {
		PublishEvent(runID, "ERROR", fmt.Sprintf("Failed to start container: %v", err))
		return false
	}

	// kafka canon :P Stream logs out of the container and into our telemetry pipeline
	out, err := cli.ContainerLogs(ctx, resp.ID, container.LogsOptions{
		ShowStdout: true,
		ShowStderr: true,
		Follow:     true,
	})
	if err == nil {
		scanner := bufio.NewScanner(out)
		for scanner.Scan() {
			PublishEvent(runID, "LOG", scanner.Text())
		}
	}

	// Wait for completion and inspect the exit code
	statusCh, errCh := cli.ContainerWait(ctx, resp.ID, container.WaitConditionNotRunning)
	select {
	case err := <-errCh:
		if err != nil {
			PublishEvent(runID, "ERROR", fmt.Sprintf("Container crashed: %v", err))
			return false
		}
	case status := <-statusCh:
		if status.StatusCode != 0 {
			PublishEvent(runID, "ERROR", fmt.Sprintf("Command failed with exit code: %d", status.StatusCode))
			return false
		}
	}

	PublishEvent(runID, "LOG", "Container execution succeeded.")
	return true
}

// THE ORCHESTRATOR
func RunPipeline(runID string, repoURL string) {
	PublishEvent(runID, "STATUS", "RUNNING") // Tell steward the job started

	workspace := filepath.Join(os.TempDir(), fmt.Sprintf("bellboy-run-%s", runID))
	os.MkdirAll(workspace, os.ModePerm)
	defer os.RemoveAll(workspace)

	cloneCmd := fmt.Sprintf("git clone %s .", repoURL)
	if !executeShellCommand(runID, cloneCmd, workspace) {
		PublishEvent(runID, "STATUS", "FAILED")
		return
	}

	configPath := filepath.Join(workspace, ".bellboy.yml")
	config, err := parseConfiguration(configPath)
	if err != nil {
		PublishEvent(runID, "ERROR", fmt.Sprintf("Failed to parse YAML: %v", err))
		PublishEvent(runID, "STATUS", "FAILED")
		return
	}

	for _, stage := range config.Pipeline.Stages {
		for taskName, task := range config.Pipeline.Tasks {
			if task.Stage == stage {
				PublishEvent(runID, "LOG", fmt.Sprintf("-> Executing Task: %s", taskName))
				for _, cmd := range task.Commands {
					if !executeShellCommand(runID, cmd, workspace) {
						PublishEvent(runID, "STATUS", "FAILED") // Halt and notify steward
						return
					}
				}
			}
		}
	}

	// catch artifacts before we nuke the workspace
	for _, artifactPath := range config.Pipeline.Artifacts {
		if !UploadArtifact(runID, workspace, artifactPath) {
			PublishEvent(runID, "STATUS", "FAILED")
			return
		}
	}

	PublishEvent(runID, "STATUS", "SUCCESS") // Tell steward the pipeline finished
}