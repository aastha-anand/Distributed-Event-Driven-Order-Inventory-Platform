package com.example.inventorysystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiSecurityFilterTest {

    private static final String KEY = "test-api-key";
    private static final String SECRET = "test-webhook-secret";

    private final ApiSecurityFilter filter =
            new ApiSecurityFilter(KEY, SECRET, 300);

    private record Result(int status, boolean passedThrough) {
    }

    private Result call(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // MockFilterChain records the request only if the filter forwarded it.
        return new Result(response.getStatus(), chain.getRequest() != null);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRequestURI(uri);
        return r;
    }

    @Test
    void apiCallWithoutKeyIsRejected() throws Exception {
        Result res = call(request("GET", "/api/products/1"));
        assertEquals(401, res.status());
        assertFalse(res.passedThrough());
    }

    @Test
    void apiCallWithWrongKeyIsRejected() throws Exception {
        MockHttpServletRequest r = request("GET", "/api/products/1");
        r.addHeader("X-API-Key", "nope");
        assertEquals(401, call(r).status());
    }

    @Test
    void apiCallWithCorrectKeyPasses() throws Exception {
        MockHttpServletRequest r = request("GET", "/api/products/1");
        r.addHeader("X-API-Key", KEY);
        assertTrue(call(r).passedThrough());
    }

    @Test
    void actuatorIsOpen() throws Exception {
        assertTrue(call(request("GET", "/actuator/health/readiness"))
                .passedThrough());
    }

    @Test
    void paymentWebhookRejectsApiKeyAlone() throws Exception {
        MockHttpServletRequest r =
                request("POST", "/api/orders/5/payment/success");
        r.addHeader("X-API-Key", KEY);
        assertEquals(401, call(r).status());
    }

    @Test
    void paymentWebhookAcceptsValidSignature() throws Exception {
        String path = "/api/orders/5/payment/success";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        MockHttpServletRequest r = request("POST", path);
        r.addHeader("X-Webhook-Timestamp", ts);
        r.addHeader("X-Webhook-Signature",
                WebhookSignature.sign(SECRET, ts, "POST", path));
        assertTrue(call(r).passedThrough());
    }

    @Test
    void signatureForAnotherOrderIsRejected() throws Exception {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        MockHttpServletRequest r =
                request("POST", "/api/orders/6/payment/failure");
        r.addHeader("X-Webhook-Timestamp", ts);
        r.addHeader("X-Webhook-Signature", WebhookSignature.sign(
                SECRET, ts, "POST", "/api/orders/5/payment/failure"));
        assertEquals(401, call(r).status());
    }

    @Test
    void blankSecretsFailFast() {
        assertThrows(IllegalStateException.class,
                () -> new ApiSecurityFilter(" ", SECRET, 300));
        assertThrows(IllegalStateException.class,
                () -> new ApiSecurityFilter(KEY, "", 300));
    }
}
