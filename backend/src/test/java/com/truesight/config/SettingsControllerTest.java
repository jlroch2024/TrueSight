package com.truesight.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/settings on its own. The token is set here, rather than read from a developer's {@code .env}, so the test
 * gives the same answer on every computer.
 */
@AutoConfigureMockMvc(addFilters = false) // security is tested separately, in SettingsIT
@WebMvcTest(controllers = SettingsController.class, properties = "truesight.logo-dev.token=pk_settings_test")
class SettingsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void givesTheLogoTokenFromTheSettings() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoToken").value("pk_settings_test"));
    }

    @Test
    void givesAnEmptyLogoTokenWhenNoneIsSet() {
        assertThat(new SettingsController("").settings().logoToken()).isEmpty();
        assertThat(new SettingsController(null).settings().logoToken()).isEmpty();
    }

    @Test
    void trimsSpacesAroundTheLogoToken() {
        assertThat(new SettingsController("  pk_settings_test \n").settings().logoToken()).isEqualTo("pk_settings_test");
    }
}
