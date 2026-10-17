package com.truesight.config;

import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.isA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/settings through the whole app, with its log-in rule. What the token is depends on the computer (a
 * developer may have one in {@code .env}, CI has none), so this checks only that one comes back as text;
 * {@link SettingsControllerTest} checks its value.
 */
@IntegrationTest
class SettingsIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestLogins logins;

    @Test
    void aLoggedInUserGetsTheSettings() throws Exception {
        mockMvc.perform(get("/api/settings").header("Authorization", logins.bearer("settings@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoToken", isA(String.class)));
    }

    @Test
    void theSettingsNeedALogIn() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please log in."));
    }
}
