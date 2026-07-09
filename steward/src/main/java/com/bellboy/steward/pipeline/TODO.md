## Stuff to do after MVP

#### adding stuff as i go

* Switching DBs without rewriting code (H2 $\rightarrow$ PostgreSQL):
    Because I used Spring Data JPA, the database dialect is abstracted. Moving from in-memory H2 to a persistent, production-grade PostgreSQL container, will literally only change 3 lines in application.properties config file. The Java code remains completely untouched.

* Handling Concurrency (Optimistic Locking):
    What happens if the pipeline is running, and two different processes (like a Go runner updating status to SUCCESS and a timeout cron job trying to cancel it) try to update the same database row at the exact same millisecond?

    * The future handle: will introduce Optimistic Locking by adding a @Version field to the PipelineRun entity. ensuring Hibernate will throw an  exception if a race condition occurs, allowing the system to handle concurrent updates safely.

* Relational Logging (@OneToMany Stage Tracking):
    rn, a PipelineRun has one broad status. In a production engine, i wanna see how long individual stages took (e.g., compile took 12s, test took 45s).

    * The future handle: will create a secondary entity called PipelineStage (with fields like stageName, status, duration) and establish a @OneToMany relationship under PipelineRun so developers can inspect their pipeline's performance step-by-step.

* Garbage Collection of Builds (Data Archiving):
    If the framework runs 10,000 builds a day, H2/PostgreSQL will quickly bloat.

    * The future handle: will write a Spring Boot @Scheduled cron job that automatically runs in the background, queries the repository for builds older than 30 days or so, archives their log payloads, and cleans up the active database.