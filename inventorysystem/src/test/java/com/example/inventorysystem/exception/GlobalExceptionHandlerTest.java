package com.example.inventorysystem.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @RestController
    static class Thrower {
        @GetMapping("/boom/illegal-state")
        String illegalState() { throw new IllegalStateException("cannot do that"); }

        @GetMapping("/boom/illegal-state-no-message")
        String illegalStateNoMessage() { throw new IllegalStateException(); }

        @GetMapping("/boom/integrity")
        String integrity() { throw new DataIntegrityViolationException("dup"); }

        @GetMapping("/boom/constraint")
        String constraint() { throw new ConstraintViolationException("bad value", Set.of()); }

        @GetMapping("/boom/unexpected")
        String unexpected() { throw new RuntimeException("secret internals"); }

        @GetMapping("/boom/status-no-reason")
        String statusNoReason() { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }

        @GetMapping("/boom/status")
        String status() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "nothing here"); }

        @GetMapping("/boom/header")
        String header(@RequestHeader("X-Needed") String v) { return v; }

        @GetMapping("/boom/number/{n}")
        String number(@PathVariable Long n) { return "ok"; }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Thrower()).setControllerAdvice(handler).build();
    }

    @Test
    void illegalStateBecomes409WithItsMessage() throws Exception {
        mvc.perform(get("/boom/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("cannot do that"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void illegalStateWithoutMessageGetsDefaultText() throws Exception {
        mvc.perform(get("/boom/illegal-state-no-message"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Operation not allowed in the current state"));
    }

    @Test
    void dataIntegrityViolationBecomes409WithoutLeakingDetails() throws Exception {
        mvc.perform(get("/boom/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A database constraint was violated"));
    }

    @Test
    void constraintViolationBecomes400() throws Exception {
        mvc.perform(get("/boom/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad value"));
    }

    @Test
    void unexpectedExceptionBecomes500WithGenericMessage() throws Exception {
        mvc.perform(get("/boom/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected server error"));
    }

    @Test
    void responseStatusExceptionKeepsStatusAndReason() throws Exception {
        mvc.perform(get("/boom/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("nothing here"));
    }

    @Test
    void responseStatusExceptionWithoutReasonGetsDefaultText() throws Exception {
        mvc.perform(get("/boom/status-no-reason"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Request failed"));
    }

    @Test
    void missingHeaderBecomes400NamingTheHeader() throws Exception {
        mvc.perform(get("/boom/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required header: X-Needed"));
    }

    @Test
    void typeMismatchBecomes400NamingTheParameter() throws Exception {
        mvc.perform(get("/boom/number/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: n"));
    }

    @Test
    void springMvcOwnErrorsKeepTheirStatus() throws Exception {
        // POST to a GET-only route: Spring raises a 405 that implements ErrorResponse.
        mvc.perform(post("/boom/status"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void errorResponseExceptionPassedDirectlyKeepsItsStatus() {
        ResponseEntity<Map<String, Object>> res =
                handler.handleUnexpected(new ResponseStatusException(HttpStatus.BAD_GATEWAY));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(res.getBody()).containsEntry("status", 502);
        assertThat(res.getBody()).doesNotContainKey("details");
        assertThat(res.getBody()).containsKey("timestamp");
    }
}
