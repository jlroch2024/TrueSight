package com.truesight.health;

import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
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

    @Autowired
    TestLogins logins;

    @Test
    void theWholeAppAnswers() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void theHealthCheckNeedsNoLogIn() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void anUnknownAddressGivesTheSharedErrorShape() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").header("Authorization", logins.bearer("health@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not found."));
    }

    @Test
    void anyOtherAddressWithoutALogInIsRefusedInTheSharedErrorShape() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please log in."));
    }
}
