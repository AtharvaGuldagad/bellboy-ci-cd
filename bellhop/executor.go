package main

//Generated
import (
	"bufio"
	"log"
	"os"
	"os/exec"

	"fmt"
	"path/filepath"

	"gopkg.in/yaml.v3"
)

// POJOs
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

// jackson still helping here =
func parseConfiguration(filePath string) (*BellboyConfig, error) { // generated function, yet to learn
	// Read the entire file from disk into memory
	data, err := os.ReadFile(filePath)
	if err != nil {
		return nil, err
	}

	var config BellboyConfig
	// Unmarshal strictly binds the YAML text to our Structs
	err = yaml.Unmarshal(data, &config)
	if err != nil {
		return nil, err
	}

	return &config, nil
}

// ProccessBuilder
func executeShellCommand(command string, workspace string) bool {
	log.Printf("Executing native command: %s\n", command)

	cmd := exec.Command("bash", "-c", command)

	cmd.Dir = workspace

	// Attach to the Standard Output pipe
	stdoutPipe, err := cmd.StdoutPipe()
	if err != nil {
		log.Printf("Failed to attach log pipe: %v\n", err)
		return false
	}

	// Merge Error stream into Standard Output so we capture everything chronologically
	cmd.Stderr = cmd.Stdout

	// Start the OS process (non-blocking)
	if err := cmd.Start(); err != nil {
		log.Printf("Failed to start command: %v\n", err)
		return false
	}

	// Stream logs line-by-line in real time
	scanner := bufio.NewScanner(stdoutPipe)
	for scanner.Scan() {
		log.Printf("[Agent Log] %s\n", scanner.Text())
	}

	// Wait for the process to finish and check the exit code
	err = cmd.Wait()
	if err != nil {
		log.Printf("Command failed: %v\n", err)
		return false // Triggers our DoNotDisturb halt
	}

	log.Printf("Command succeeded.\n")
	return true
}

// Add this to executor.go

// RunPipeline orchestrates the entire build process natively in Go
func RunPipeline(runID string, repoURL string) {
	log.Printf("[Run %s] Starting execution agent...", runID)

	// 1. Create Workspace
	workspace := filepath.Join(os.TempDir(), fmt.Sprintf("bellboy-run-%s", runID))
	os.MkdirAll(workspace, os.ModePerm)

	// 2. THE CLEANUP (Go's equivalent of the Java 'finally' block)
	defer os.RemoveAll(workspace)
	log.Printf("[Run %s] Created workspace: %s", runID, workspace)

	// 3. Git Clone
	cloneCmd := fmt.Sprintf("git clone %s .", repoURL)
	if !executeShellCommand(cloneCmd, workspace) {
		log.Printf("[Run %s] FATAL: Git clone failed.", runID)
		return
	}

	// 4. Parse YAML
	configPath := filepath.Join(workspace, ".bellboy.yml")
	config, err := parseConfiguration(configPath)
	if err != nil {
		log.Printf("[Run %s] FATAL: Failed to parse .bellboy.yml: %v", runID, err)
		return
	}

	log.Printf("[Run %s] Pipeline Name: %s", runID, config.Pipeline.Name)

	// 5. THE EXECUTION LOOP (Stages -> Tasks -> Commands)
	for _, stage := range config.Pipeline.Stages {
		log.Printf("=== Stage: %s ===", stage)

		for taskName, task := range config.Pipeline.Tasks {
			if task.Stage == stage {
				log.Printf("-> Executing Task: %s", taskName)

				for _, cmd := range task.Commands {
					if !executeShellCommand(cmd, workspace) {
						log.Printf("[Run %s] FATAL: Task '%s' failed on command: %s", runID, taskName, cmd)
						return // Halt pipeline immediately (DoNotDisturb)
					}
				}
			}
		}
	}

	log.Printf("[Run %s] Pipeline execution completed successfully!", runID)
}
