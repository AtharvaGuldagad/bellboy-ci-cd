package main

import (
	"context"
	"fmt"
	"log"
	"path/filepath"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

var minioClient *minio.Client
const bucketName = "bellboy-artifacts"

// InitStorage establishes the connection and provisions the bucket if it doesn't exist
func InitStorage() {
	endpoint := "localhost:9000"
	accessKeyID := "bellboy_admin"
	secretAccessKey := "bellboy_secret_key"
	useSSL := false

	var err error
	minioClient, err = minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKeyID, secretAccessKey, ""),
		Secure: useSSL,
	})
	if err != nil {
		log.Fatalf("Failed to initialize MinIO client: %v", err)
	}

	// Safely ensure our artifact bucket exists on startup
	ctx := context.Background()
	err = minioClient.MakeBucket(ctx, bucketName, minio.MakeBucketOptions{Region: "us-east-1"})
	if err != nil {
		exists, errBucketExists := minioClient.BucketExists(ctx, bucketName)
		if errBucketExists == nil && exists {
			log.Printf("MinIO bucket '%s' already exists and is ready.", bucketName)
		} else {
			log.Fatalf("Failed to verify or create bucket: %v", err)
		}
	} else {
		log.Printf("Successfully provisioned MinIO bucket: %s", bucketName)
	}
}

// UploadArtifact pushes a file from the ephemeral workspace to persistent S3 storage
func UploadArtifact(runID string, workspace string, artifactPath string) bool {
	ctx := context.Background()
	
	// Where the file lives right now on the disk
	localFilePath := filepath.Join(workspace, artifactPath)
	
	// How it will be organized in the bucket: e.g., "run-1234/target/app.jar"
	objectName := fmt.Sprintf("%s/%s", runID, artifactPath)

	PublishEvent(runID, "LOG", fmt.Sprintf("Uploading artifact to vault: %s", artifactPath))

	// Stream the file directly from disk to the network
	_, err := minioClient.FPutObject(ctx, bucketName, objectName, localFilePath, minio.PutObjectOptions{
		ContentType: "application/octet-stream",
	})
	
	if err != nil {
		PublishEvent(runID, "ERROR", fmt.Sprintf("Artifact upload failed for %s: %v", artifactPath, err))
		return false
	}

	PublishEvent(runID, "LOG", fmt.Sprintf("Successfully secured artifact: %s", objectName))
	return true
}