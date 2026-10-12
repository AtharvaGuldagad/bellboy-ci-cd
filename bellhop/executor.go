package main

import (
	"bufio"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"

	"gopkg.in/yaml.v3"
)

// 1. DATA MODELS
type BellboyConfig struct {
	Pipeline PipelineDef `yaml:"pipeline"`
}

type PipelineDef struct {
	Name   string             `yaml:"name"`
	Stages []string           `yaml:"stages"`
	Tasks  map[string]TaskDef `yaml:"tasks"`
}

type TaskDef struct {
	Stage    string   `yaml:"stage"`
	Commands []string `yaml:"commands"`
}

// 2. THE PARSER
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

// 3. THE NATIVE EXECUTOR (Now with Kafka Telemetry!)
func executeShellCommand(runID string, command string, workspace string) bool {
	PublishEvent(runID, "LOG", fmt.Sprintf("Executing: %s", command))

	cmd := exec.Command("bash", "-c", command)
	cmd.Dir = workspace

	stdoutPipe, err := cmd.StdoutPipe()
	if err != nil {
		PublishEvent(runID, "ERROR", fmt.Sprintf("Failed to attach log pipe: %v", err))
		return false
	}

	cmd.Stderr = cmd.Stdout

	if err := cmd.Start(); err != nil {
		PublishEvent(runID, "ERROR", fmt.Sprintf("Failed to start command: %v", err))
		return false
	}

	// Stream logs line-by-line in real time into Kafka
	scanner := bufio.NewScanner(stdoutPipe)
	for scanner.Scan() {
		PublishEvent(runID, "LOG", scanner.Text())
	}

	err = cmd.Wait()
	if err != nil {
		PublishEvent(runID, "ERROR", fmt.Sprintf("Command failed: %v", err))
		return false
	}

	PublishEvent(runID, "LOG", "Command succeeded.")
	return true
}

// 4. THE ORCHESTRATOR
func RunPipeline(runID string, repoURL string) {
	PublishEvent(runID, "STATUS", "RUNNING") // Tell Java the job started!

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
						PublishEvent(runID, "STATUS", "FAILED") // Halt and notify Java
						return
					}
				}
			}
		}
	}

	PublishEvent(runID, "STATUS", "SUCCESS") // Tell Java the pipeline finished!
}
