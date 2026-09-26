package com.truesight.config;

import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the backend serves the website the way the live site needs: pages such as {@code /portfolios/1} open
 * directly, while {@code /api/} addresses and missing files still answer "Not found."
 *
 * <p>It uses a stand-in {@code index.html} from the test resources, since the real one only exists in the live
 * site's build.
 */
@IntegrationTest
class WebsiteIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestLogins logins;

    @Test
    void theHomePageIsTheWebsite() throws Exception {
        // Spring Boot answers "/" by handing over to index.html. MockMvc notes the hand-over without following it.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test website")));
    }

    @Test
    void aWebsitePageOpensDirectly() throws Exception {
        mockMvc.perform(get("/portfolios/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test website")));

        mockMvc.perform(get("/portfolios/1/supply-chain"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test website")));
    }

    @Test
    void anUnknownApiAddressIsStillNotFound() throws Exception {
        mockMvc.perform(get("/api/no-such-thing").header("Authorization", logins.bearer("website@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not found."));
    }

    @Test
    void aMissingFileIsNotFound() throws Exception {
        mockMvc.perform(get("/assets/missing.js"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theSwaggerPageStillOpens() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("TrueSight API"));
    }
}
