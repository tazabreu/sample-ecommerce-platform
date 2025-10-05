package com.ecommerce.customer.testsupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JwtTestUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String DEFAULT_SECRET = "demo-secret-key-for-local-development-only-do-not-use-in-production";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JwtTestUtils() {}

    public static String managerToken() {
        return generateToken(List.of("MANAGER"));
    }

    public static String userToken() {
        return generateToken(List.of("USER"));
    }

    public static String generateToken(List<String> roles) {
        try {
            Map<String, Object> header = Map.of(
                    "alg", "HS256",
                    "typ", "JWT"
            );

            long now = Instant.now().getEpochSecond();
            long exp = now + 3600; // 1 hour

            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", "test-user");
            payload.put("roles", roles);
            payload.put("iat", now);
            payload.put("exp", exp);
            payload.put("iss", "ecommerce-platform-dev");

            String headerJson = OBJECT_MAPPER.writeValueAsString(header);
            String payloadJson = OBJECT_MAPPER.writeValueAsString(payload);

            String headerB64 = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadB64 = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = hmacSha256(DEFAULT_SECRET, headerB64 + "." + payloadB64);

            return headerB64 + "." + payloadB64 + "." + signature;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to generate JWT for tests", e);
        }
    }

    private static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT for tests", e);
        }
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}


