package com.truesight.user;

import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@link CurrentUser} answers with whoever the token belongs to, not with a fixed user, and that the demo
 * account can log in on laptops and in tests.
 */
@IntegrationTest
class CurrentUserIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestLogins logins;

    @Test
    void theCurrentUserIsWhoeverTheTokenBelongsTo() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", logins.bearer("first@example.com")))
                .andExpect(jsonPath("$.email").value("first@example.com"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", logins.bearer("second@example.com")))
                .andExpect(jsonPath("$.email").value("second@example.com"));
    }

    @Test
    void theDemoAccountCanLogIn() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + DemoUserSeeder.DEMO_EMAIL + "\",\"password\":\""
                                + DemoUserSeeder.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DemoUserSeeder.DEMO_EMAIL));
    }
}
