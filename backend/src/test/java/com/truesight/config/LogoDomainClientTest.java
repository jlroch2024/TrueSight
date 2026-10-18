package com.truesight.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Checks what is asked of Logo.dev's brand search, against a fake server: no request leaves the computer. The answers
 * here only have the shape of Logo.dev's (a list of brands, each with a name and a domain); they are not real answers.
 */
class LogoDomainClientTest {

    private static final String URL = "https://api.logo.dev/search?q=Carl%20Zeiss%20SMT";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void asksBySearchingTheNameWithTheSecretKeyAndTakesTheFirstBrandsDomain() {
        LogoDomainClient client = new LogoDomainClient(builder, "sk_test");
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer sk_test"))
                .andRespond(withSuccess("""
                        [{"name": "Example First", "domain": "first.example"},
                         {"name": "Example Second", "domain": "second.example"}]""", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Carl Zeiss SMT")).contains("first.example");
        server.verify();
    }

    @Test
    void findsNothingWhenNoBrandMatches() {
        LogoDomainClient client = new LogoDomainClient(builder, "sk_test");
        server.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Carl Zeiss SMT")).isEmpty();
    }

    @Test
    void findsNothingWhenLogoDevFails() {
        LogoDomainClient client = new LogoDomainClient(builder, "sk_test");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.domainOf("Carl Zeiss SMT")).isEmpty();
    }

    @Test
    void asksNothingWithoutASecretKey() {
        LogoDomainClient client = new LogoDomainClient(builder, "  ");

        assertThat(client.domainOf("Carl Zeiss SMT")).isEmpty();
        server.verify(); // no request was made
    }
}
