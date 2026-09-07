package main

import (
	"fmt"
	"log"
	"net/http"
)

// HealthCheckHandler responds to liveness probes
func HealthCheckHandler(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "{\"status\": \"UP\", \"service\": \"bellhop-agent\"}")
}

func main() {
	//Route Regis
	http.HandleFunc("/api/v1/health", HealthCheckHandler)

	port := ":8081" // Go agent on 8081, spring on 8080
	log.Printf("Bellhop Agent starting on port %s...\n", port)

	err := http.ListenAndServe(port, nil)
	if err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
