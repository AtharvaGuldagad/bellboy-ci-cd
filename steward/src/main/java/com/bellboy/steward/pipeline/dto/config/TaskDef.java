package com.bellboy.steward.pipeline.dto.config;

import lombok.Data;
import java.util.List;

@Data
public class TaskDef {
    private String stage;
    private String description;
    private List<String> commands;
    private ArtifactsDef artifacts;
}