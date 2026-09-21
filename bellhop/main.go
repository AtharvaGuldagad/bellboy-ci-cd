package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
)

// JobRequest defines the payload pushed by the Java Control Plane (Steward)
type JobRequest struct {
	RunID   string `json:"run_id"`
	RepoURL string `json:"repo_url"`
}

// HealthCheckHandler responds to liveness probes from Docker/Kubernetes
func HealthCheckHandler(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Header().Set("Content-Type", "application/json")
	fmt.Fprintf(w, `{"status": "UP", "service": "bellhop-agent"}`)
}

// ExecuteJobHandler receives the Push event from Steward and triggers execution
func ExecuteJobHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var job JobRequest
	if err := json.NewDecoder(r.Body).Decode(&job); err != nil {
		log.Printf("[Agent] Failed to decode job payload: %v\n", err)
		http.Error(w, "Invalid JSON payload", http.StatusBadRequest)
		return
	}

	log.Printf("[DISPATCH] Received Job | Run ID: %s | Repo: %s\n", job.RunID, job.RepoURL)

	// 1. NON-BLOCKING ACKNOWLEDGMENT
	// Instantly release the Java HTTP connection back to its thread pool
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusAccepted)
	fmt.Fprintf(w, `{"message": "Job accepted by Bellhop", "run_id": "%s"}`, job.RunID)

	// 2. ASYNC EXECUTION (The Goroutine Trigger)
	// Spawns a lightweight, non-blocking background task to run the pipeline
	go RunPipeline(job.RunID, job.RepoURL)
}

func main() {
	// Route Registration
	http.HandleFunc("/api/v1/health", HealthCheckHandler)
	http.HandleFunc("/api/v1/execute", ExecuteJobHandler) // The Push Endpoint

	port := ":8081" // Go agent on 8081, Spring Boot on 8080
	log.Printf("Bellhop Agent starting on port %s...\n", port)

	err := http.ListenAndServe(port, nil)
	if err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
