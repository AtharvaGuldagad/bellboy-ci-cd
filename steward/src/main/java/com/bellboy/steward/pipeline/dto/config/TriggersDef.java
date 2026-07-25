package com.bellboy.steward.pipeline.dto.config;
import lombok.Data;
import java.util.List;

@Data
public class TriggersDef {
    private List<String> branches;
}
