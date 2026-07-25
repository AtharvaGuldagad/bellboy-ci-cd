# BellBoy Config Guide (The TL;DR)

Alright so here's how u set up the `.bellboy.yml` config file for ur repos. Think of it as the instruction manual for ur code. It tells the BellBoy engine exactly what environment u need and what commands to run.

---
## Here's a sample config:

```yaml
    pipeline:
        name: "Spring Boot Microservice Build"
  
        runner:
            image: "maven:3.8-openjdk-17"
    
        triggers:
            branches: ["main", "develop"]

        stages:
            - compile
            - test
            - deploy

        tasks:
            build-app:
                stage: compile
                commands: ["mvn clean compile"]
      
            unit-test:
                stage: test
                commands: ["mvn test"]
      
            security-scan:
                stage: test
                commands: ["./trivy fs ."]
      
            package:
                stage: deploy
                commands: ["mvn package -DskipTests"]
                artifacts:
                    paths: ["target/*.jar"]
```
## 1. The Basics & Env

```yaml
pipeline:
  name: "Spring Boot Microservice Build"
  
  runner:
    image: "maven:3.8-openjdk-17"
    
  triggers:
    branches: ["main", "develop"]

```

* **`name`**: Literally just what it shows up as in the UI/DB.
* **`runner.image`**: This is imp. Instead of running stuff directly on the host OS (and getting the "But it works on my machine" error lol), BellBoy spins up this exact Docker container to run ur code in a clean environment.
* **`triggers.branches`**: Pls use this so we don't trigger a whole pipeline run every time someone pushes to `fix/typo`. It only runs if the push matches the branches u list here.

---

## 2. Stages (The Timeline)

```yaml
  stages:
    - compile
    - test
    - deploy

```

This is just the order of operations. It goes strictly top to bottom.

> **Note:** It’s **fail-fast** — meaning if the `compile` stage fails, the runner just stops and skips the rest. No point testing broken code.

---

## 3. Tasks (The Actual Work)

```yaml
  tasks:
    build-app:
      stage: compile
      commands: ["mvn clean compile"]
      
    unit-test:
      stage: test
      commands: ["mvn test"]
      
    security-scan:
      stage: test
      commands: ["./trivy fs ."]

```

Tasks are where the actual muscle is. U tie every task to a stage.

* **Parallel Execution:** Notice how `unit-test` and `security-scan` are both mapped to the `test` stage? A solid CI engine will run those in parallel so u aren't waiting forever for ur build to finish.
* **`commands`**: Literally just an array of what u would type into ur own terminal.

---

## 4. Artifacts (Don't lose ur stuff)

```yaml
    package:
      stage: deploy
      commands: ["mvn package -DskipTests"]
      artifacts:
        paths: ["target/*.jar"]

```

Because CI/CD containers are **ephemeral** (meaning they get completely nuked after the pipeline finishes), u lose everything unless u tell it to save it.

The `artifacts` tag tells BellBoy to grab ur compiled `.jar` file before destroying the workspace, so u can actually deploy it to production later.