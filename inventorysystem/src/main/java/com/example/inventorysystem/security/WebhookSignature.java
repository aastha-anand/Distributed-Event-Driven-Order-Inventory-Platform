package com.example.inventorysystem.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public final class WebhookSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSignature() {
    }

    public static String sign(
            String secret, String timestamp, String method, String path) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(
                    (timestamp + "." + method.toUpperCase() + "." + path)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    public static boolean verify(
            String secret,
            String timestamp,
            String method,
            String path,
            String signature,
            long toleranceSeconds,
            Instant now) {

        if (timestamp == null || signature == null) {
            return false;
        }

        long sentAt;
        try {
            sentAt = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }

        if (Math.abs(now.getEpochSecond() - sentAt) > toleranceSeconds) {
            return false;
        }

        String expected = sign(secret, timestamp, method, path);
        // Constant-time comparison: do not leak how many leading chars matched.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase()
                        .getBytes(StandardCharsets.UTF_8));
    }
}
