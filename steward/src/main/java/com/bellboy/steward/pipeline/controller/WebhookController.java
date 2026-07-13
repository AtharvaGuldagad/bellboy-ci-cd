package com.bellboy.steward.pipeline.controller;

import com.bellboy.steward.pipeline.dto.WebhookPayload;
import com.bellboy.steward.pipeline.service.PipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final PipelineService pipelineService;

    @PostMapping("/check-in")
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody WebhookPayload payload) {
        
        UUID runId = pipelineService.registerNewRun(payload);

        // respponse body
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Pipeline run queued successfully.");
        response.put("run_id", runId);
        response.put("status", "PENDING");

        // Return 202 Accepted
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
