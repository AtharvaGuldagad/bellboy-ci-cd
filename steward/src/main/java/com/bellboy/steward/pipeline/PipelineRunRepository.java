package com.bellboy.steward.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {
    //TODO: Add custom query methods later after MVP: findByStatus, findByRepoURL,etc
}
