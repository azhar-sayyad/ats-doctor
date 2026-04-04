package com.atsdoctor.backend;

import com.atsdoctor.backend.api.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-089 — the global fallback advice maps any exception no controller
 * handler declares to a stable, sanitized RFC 7807 500 (no internals leaked),
 * while ResponseStatusException keeps its own status code.
 */
@WebMvcTest(value = GlobalExceptionHandlerTest.FaultController.class, properties = "ats.doctor.persistence.enabled=true")
@Import({GlobalExceptionHandlerTest.AdviceConfig.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @RestController
    static class FaultController {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/conflict")
        String conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already in progress");
        }
    }

    @TestConfiguration
    static class AdviceConfig {
        @Bean
        FaultController faultController() {
            return new FaultController();
        }
    }

    @Test
    void unhandled_exceptions_map_to_a_sanitized_500() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void response_status_exceptions_keep_their_status() throws Exception {
        mvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
