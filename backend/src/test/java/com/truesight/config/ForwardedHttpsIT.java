package com.truesight.config;

import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cloud Run receives https and hands the app plain http, saying which it was in X-Forwarded-Proto. With
 * {@code server.forward-headers-strategy: framework} the app trusts that header, so it knows its own address is https:
 * the Swagger page then sends its requests to https (a browser blocks http from an https page), and the HSTS header,
 * which Spring Security sends only on https, tells browsers never to use http for the site.
 */
@IntegrationTest
class ForwardedHttpsIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void aRequestForwardedAsHttpsGetsHstsAndSwaggerAddressesItAsHttps() throws Exception {
        mockMvc.perform(get("/api/health").header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "truesight.example"))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", startsWith("max-age=31536000")));

        mockMvc.perform(get("/v3/api-docs").header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "truesight.example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("https://truesight.example"));
    }

    @Test
    void plainHttpOnALaptopGetsNoHsts() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
