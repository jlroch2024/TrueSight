package com.truesight.health;

import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The same endpoint as {@link HealthControllerTest}, but through the whole app and a real database. Copy this for
 * any endpoint that reads or writes the database.
 *
 * <p>If this test starts at all, it has already proved two things: Flyway created the tables, and every Java
 * entity matches its table, because Hibernate refuses to start otherwise.
 */
@IntegrationTest
class HealthIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void theWholeAppAnswers() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void anUnknownAddressGivesTheSharedErrorShape() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not found."));
    }
}
