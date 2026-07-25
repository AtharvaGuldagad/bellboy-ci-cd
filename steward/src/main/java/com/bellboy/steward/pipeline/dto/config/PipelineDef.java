package com.bellboy.steward.pipeline.dto.config;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PipelineDef {
    private String name;
    private RunnerDef runner;       
    private TriggersDef triggers;   
    private Map<String, String> environment;
    private List<String> stages;
    private Map<String, TaskDef> tasks;
}