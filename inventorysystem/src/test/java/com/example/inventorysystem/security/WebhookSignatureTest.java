package com.example.inventorysystem.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureTest {

    private static final String SECRET = "s3cret";
    private static final String PATH = "/api/orders/7/payment/success";
    private static final Instant NOW = Instant.ofEpochSecond(1_800_000_000L);
    private static final String TS = String.valueOf(NOW.getEpochSecond());

    @Test
    void signatureIsDeterministicHex() {
        String a = WebhookSignature.sign(SECRET, TS, "POST", PATH);
        assertEquals(a, WebhookSignature.sign(SECRET, TS, "post", PATH));
        assertEquals(64, a.length());
    }

    @Test
    void validSignatureIsAccepted() {
        String sig = WebhookSignature.sign(SECRET, TS, "POST", PATH);
        assertTrue(WebhookSignature.verify(
                SECRET, TS, "POST", PATH, sig, 300, NOW));
    }

    @Test
    void wrongSecretPathMethodOrSignatureIsRejected() {
        String sig = WebhookSignature.sign(SECRET, TS, "POST", PATH);
        assertFalse(WebhookSignature.verify("other", TS, "POST", PATH, sig, 300, NOW));
        assertFalse(WebhookSignature.verify(SECRET, TS, "POST",
                "/api/orders/8/payment/success", sig, 300, NOW));
        assertFalse(WebhookSignature.verify(SECRET, TS, "GET", PATH, sig, 300, NOW));
        assertFalse(WebhookSignature.verify(SECRET, TS, "POST", PATH, "deadbeef", 300, NOW));
    }

    @Test
    void staleOrFutureTimestampIsRejected() {
        String oldTs = String.valueOf(NOW.getEpochSecond() - 301);
        String sig = WebhookSignature.sign(SECRET, oldTs, "POST", PATH);
        assertFalse(WebhookSignature.verify(SECRET, oldTs, "POST", PATH, sig, 300, NOW));

        String futureTs = String.valueOf(NOW.getEpochSecond() + 301);
        String sig2 = WebhookSignature.sign(SECRET, futureTs, "POST", PATH);
        assertFalse(WebhookSignature.verify(SECRET, futureTs, "POST", PATH, sig2, 300, NOW));
    }

    @Test
    void missingOrMalformedInputsAreRejected() {
        String sig = WebhookSignature.sign(SECRET, TS, "POST", PATH);
        assertFalse(WebhookSignature.verify(SECRET, null, "POST", PATH, sig, 300, NOW));
        assertFalse(WebhookSignature.verify(SECRET, TS, "POST", PATH, null, 300, NOW));
        assertFalse(WebhookSignature.verify(SECRET, "not-a-number", "POST", PATH, sig, 300, NOW));
    }

    @Test
    void timestampIsPartOfTheSignedMessage() {
        assertNotEquals(
                WebhookSignature.sign(SECRET, "1", "POST", PATH),
                WebhookSignature.sign(SECRET, "2", "POST", PATH));
    }
}
