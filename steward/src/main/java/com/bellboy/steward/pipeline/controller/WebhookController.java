package com.bellboy.steward.pipeline.controller;

import com.bellboy.steward.pipeline.dto.WebhookPayload;
import com.bellboy.steward.pipeline.service.PipelineService;
import com.bellboy.steward.security.HmacValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final PipelineService pipelineService;
    private final HmacValidator hmacValidator;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String rawPayload) {

        // 1. THE BOUNCER: Cryptographic validation before doing anything else
        if (!hmacValidator.isValidSignature(rawPayload, signature)) {
            log.error("SECURITY ALERT: Webhook signature validation failed. Rejecting payload.");
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid or missing HMAC signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }

        log.info("Webhook signature verified successfully.");

        try {
            WebhookPayload payload = objectMapper.readValue(rawPayload, WebhookPayload.class);

            UUID runId = pipelineService.registerNewRun(payload);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Pipeline run queued successfully.");
            response.put("run_id", runId);
            response.put("status", "PENDING");

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (Exception e) {
            log.error("Failed to parse valid webhook payload", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Malformed JSON payload");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
}