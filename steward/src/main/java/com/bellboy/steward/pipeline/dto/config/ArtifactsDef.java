package com.bellboy.steward.pipeline.dto.config;

import lombok.Data;
import java.util.List;

@Data
public class ArtifactsDef {
    private List<String> paths;
}