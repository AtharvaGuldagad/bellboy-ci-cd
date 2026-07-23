# BellBoy: A Distributed CI/CD Automation Framework

BellBoy is a custom, lightweight, event-driven CI/CD framework built from scratch to demystify continuous integration and automated deployment lifecycles.

Unlike standard pipeline configurations using managed services (e.g., GitHub Actions), BellBoy implements its own state machine, event queue, config parser, and process execution runner. The architecture strictly decouples the Control Plane (the orchestrator) from the Data Plane (the execution agent).

## Architectural Overview

 The framework is structured as a decoupled distributed system using an event-driven model:

1. Ingress Layer (Kong API Gateway): Protects the orchestrator by handling authentication (JWT verification) and verifying incoming Git webhook payloads.

2. Control Plane (Steward - Spring Boot): Manages the core pipeline state machine, persists tracking metadata to a transactional database, and dispatches job tokens.

3. Message Backbone (Apache Kafka): Asynchronously queues incoming build tasks to guarantee system resilience under concurrent workloads.

4. Data Plane (Bellhop - Go): A highly performant execution agent that consumes jobs from Kafka, clones source repositories, parses custom configuration rules, executes commands in isolated environments, and streams execution logs back to the orchestrator.

## Core Architecture Flow

                  +-------------------------+
                  |  Developer Git Push     |
                  +------------+------------+
                               |
                               | (Webhook Event)
                               v
                  +-------------------------+
                  |  Kong API Gateway       |
                  +------------+------------+
                               |
                               | (Validated Proxy)
                               v
                  +-------------------------+
                  |  Steward Orchestrator   | <---> [ H2 / PostgreSQL ]
                  |      (Spring Boot)      |
                  +------------+------------+
                               |
                               | (Event Dispatch)
                               v
                  +-------------------------+
                  |  Apache Kafka Cluster   |
                  +------------+------------+
                               |
                               | (Consumer Fetch)
                               v
                  +-------------------------+
                  |     Bellhop Runner      | <---> [ OS / Container Sandbox ]
                  |        (Go Lang)        |
                  +-------------------------+


## Workspace Structure

The project is managed as a monorepo containing both the control and data plane subsystems:

    bellboy/
    ├── steward/                 # Control Plane (Spring Boot Orchestrator)
    │   ├── src/
    │   │   ├── main/java/com/bellboy/steward/pipeline/   # Pipeline State Domain
    │   │   └── ...
    │   └── pom.xml
    ├── bellhop/                 # (In Development) Data Plane (Go Execution Runner)
    │   ├── main.go
    │   └── ...
    ├── .gitignore               # Unified monorepo ignore rules
    └── README.md                # System documentation


## Configuration Specification (.my-ci.yaml)

* The following format is currently under consideration:

Projects processed by BellBoy define their pipeline execution lifecycle via a custom configuration file placed in their repository root:

    pipeline:
        name: "Sample Spring Build"
        environment:
        JAVA_VERSION: "17"

    stages:
        - compile
        - test

    tasks:
        compile:
            description: "Compiling source code"
            commands:
                - mvn clean compile
        test:
            description: "Running automated tests"
            commands:
            - mvn test


## Getting Started

### Prerequisites

* Java Development Kit (JDK) 17 or higher

* Maven 3.8+

* [Other prerequisites, such as Go or Kafka, will be added as components are implemented]

### Installation & Local Setup

* Clone the repository:

``` 
    git clone https://github.com/AtharvaGuldagad/bellboy.git cd bellboy 
```

* Build and run the Steward Orchestrator:

``` 
 cd steward
 mvn clean install
 mvn spring-boot:run
```

## Simulating a Pipeline Trigger (MVP Mode)

During the MVP development phase, you can simulate a Git push webhook event by firing a POST request directly to the Steward webhook receiver endpoint:
```
    curl -X POST http://localhost:8080/api/v1/webhook \
        -H "Content-Type: application/json" \
         -d '{
        "repo_url": "[https://github.com/](https://github.com/)[sample-target-repo].git",
        "commit_sha": "[sample-git-commit-hash]"
    }'
```

## Roadmap & Milestone Targets

### Milestone 1: Monolithic MVP (In Progress)

[x] Design Core Pipeline state machine and H2 database models.

[x] Implement local HTTP webhook ingestion endpoint.

[x] Build monolithic Java execution runner using local operating system subprocesses.

### Milestone 2: Monorepo Decoupling

[ ] Implement Go Lang runner (bellhop) to parse custom YAML configurations.

[ ] Create HTTP state-synchronization endpoints between Steward and Bellhop.

### Milestone 3: Scale & Resilience

[ ] Integrate Apache Kafka to queue jobs asynchronously.

[ ] Implement Docker-in-Docker (DinD) sandbox container layer on Bellhop to isolate build environments safely.

[ ] Configure Kong Gateway edge security and JWT route validation.