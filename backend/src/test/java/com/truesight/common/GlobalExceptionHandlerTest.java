package com.truesight.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves every error leaves the backend in the same shape: the right HTTP status and a {@code message}.
 *
 * <p>It uses a small controller that exists only in this test and throws on purpose.
 */
@WebMvcTest(GlobalExceptionHandlerTest.ThrowingController.class)
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw ApiException.notFound("Portfolio not found.");
        }

        @GetMapping("/test/bad-request")
        void badRequest() {
            throw ApiException.badRequest("The file needs a ticker column.");
        }

        @GetMapping("/test/bug")
        void bug() {
            throw new IllegalStateException("database password is hunter2");
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void anApiExceptionKeepsItsStatusAndMessage() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));

        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The file needs a ticker column."));
    }

    @Test
    void anUnexpectedErrorGivesAPlainMessageAndHidesTheDetails() throws Exception {
        mockMvc.perform(get("/test/bug"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong. Please try again."));
    }
}
