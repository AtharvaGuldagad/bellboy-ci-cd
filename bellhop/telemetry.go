package main

import (
	"context"
	"encoding/json"
	"log"
	"time"

	"github.com/segmentio/kafka-go"
)

// JSON structure for telemetry events
type TelemetryEvent struct {
	RunID     string    `json:"run_id"`
	EventType string    `json:"event_type"` // log, stat update etc
	Payload   string    `json:"payload"`    // actual log line or stat
	Timestamp time.Time `json:"timestamp"`
}

var kafkaWriter *kafka.Writer

// initializes the connection to the Docker container
func InitKafka() {
	kafkaWriter = &kafka.Writer{
		Addr:     kafka.TCP("localhost:9092"),
		Topic:    "build-telemetry",
		Balancer: &kafka.LeastBytes{},
	}
	log.Println("Kafka Telemetry Producer initialized.")
}

// wraps the event in JSON and fires it into Kafka
func PublishEvent(runID, eventType, payload string) {
	event := TelemetryEvent{
		RunID:     runID,
		EventType: eventType,
		Payload:   payload,
		Timestamp: time.Now(),
	}

	eventBytes, _ := json.Marshal(event)

	err := kafkaWriter.WriteMessages(context.Background(),
		kafka.Message{
			Key:   []byte(runID), // Partitioning key (imp af)
			Value: eventBytes,
		},
	)

	if err != nil {
		log.Printf("[Telemetry Error] Failed to publish event for Run %s: %v", runID, err)
	}
}
