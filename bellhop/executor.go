package main

import (
	"bufio"
	"log"
	"os"
	"os/exec"

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
