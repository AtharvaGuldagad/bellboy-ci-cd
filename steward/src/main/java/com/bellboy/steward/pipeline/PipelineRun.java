package com.bellboy.steward.pipeline;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pipeline_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repo_url", nullable = false)
    private String repoURL;

    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSHA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PipelineStatus status;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "branch", nullable = false)
    private String branch;

    @PrePersist //hooks in JPA to set default val before persisting to DB
    protected void onCreate() {
        if (this.status == null) {
            this.status = PipelineStatus.PENDING;
        }
        if (this.startTime == null) {
            this.startTime = LocalDateTime.now();
        }
    }
}
