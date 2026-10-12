package com.bellboy.steward.security;

// Generated*** by Gemini

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Slf4j
@Component
public class HmacValidator {

    // Inject this from application.properties (e.g., bellboy.webhook.secret=my-super-secret)
    @Value("${bellboy.webhook.secret}")
    private String webhookSecret;

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    /**
     * Validates that the payload was genuinely sent by GitHub and hasn't been tampered with.
     */
    public boolean isValidSignature(String rawPayload, String headerSignature) {
        if (headerSignature == null || !headerSignature.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Missing or malformed X-Hub-Signature-256 header");
            return false;
        }

        try {
            // 1. Initialize the MAC with our shared secret
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);

            // 2. Hash the raw incoming payload
            byte[] computedHashBytes = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            
            // 3. Convert the byte array to a hex string (Java 17+ feature)
            String computedHashString = SIGNATURE_PREFIX + HexFormat.of().formatHex(computedHashBytes);

            // 4. Constant-Time Comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    computedHashString.getBytes(StandardCharsets.UTF_8),
                    headerSignature.getBytes(StandardCharsets.UTF_8)
            );

        } catch (Exception e) {
            log.error("Failed to calculate HMAC signature", e);
            return false;
        }
    }
}