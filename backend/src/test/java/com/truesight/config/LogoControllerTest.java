package com.truesight.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/logos/domains on its own, with a fake Logo.dev client: no request leaves the computer. */
@AutoConfigureMockMvc(addFilters = false) // security: every /api/ address needs a login, tested in AuthIT
@WebMvcTest(controllers = LogoController.class)
@Import(LogoDomainService.class)
class LogoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LogoDomainClient client;

    @Test
    void givesTheDomainOfEachCompanyThatHasOneAndLeavesOutTheRest() throws Exception {
        when(client.domainOf("Carl Zeiss SMT")).thenReturn(Optional.of("zeiss.example"));
        when(client.domainOf("Unknown Supplier")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/logos/domains").param("names", "Carl Zeiss SMT", "Unknown Supplier"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Carl Zeiss SMT']").value("zeiss.example"))
                .andExpect(jsonPath("$['Unknown Supplier']").doesNotExist());
    }

    @Test
    void asksLogoDevAboutEachCompanyOnlyOnce() throws Exception {
        when(client.domainOf(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/logos/domains").param("names", "Once Only Ltd")).andExpect(status().isOk());
        mockMvc.perform(get("/api/logos/domains").param("names", "Once Only Ltd")).andExpect(status().isOk());

        verify(client, times(1)).domainOf("Once Only Ltd");
    }

    @Test
    void givesNothingWhenNoNameIsAsked() throws Exception {
        mockMvc.perform(get("/api/logos/domains"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void refusesTooManyNamesAtOnce() throws Exception {
        String[] names = new String[LogoController.MAX_NAMES + 1];
        for (int i = 0; i < names.length; i++) names[i] = "Company " + i;

        mockMvc.perform(get("/api/logos/domains").param("names", names))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ask about 100 companies or fewer at a time."));
    }
}
