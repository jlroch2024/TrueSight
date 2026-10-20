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
 * here only have the shape of Logo.dev's (a list of brands, each with a name and a domain); they are not real answers,
 * except "carlzeisssmt.com", which the live site's search answered for Carl Zeiss SMT on 20 October 2026.
 */
class LogoDomainClientTest {

    private static final String URL = "https://api.logo.dev/search?q=Carl%20Zeiss%20SMT";
    // The same name without its unit word, for the second search.
    private static final String SHORTER_URL = "https://api.logo.dev/search?q=Carl%20Zeiss";

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
        server.expect(requestTo(SHORTER_URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Carl Zeiss SMT")).isEmpty();
        server.verify();
    }

    @Test
    void findsNothingWhenLogoDevFails() {
        LogoDomainClient client = new LogoDomainClient(builder, "sk_test");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(SHORTER_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.domainOf("Carl Zeiss SMT")).isEmpty();
        server.verify();
    }

    @Test
    void asksNothingWithoutASecretKey() {
        LogoDomainClient client = new LogoDomainClient(builder, "  ");

        assertThat(client.domainOf("Carl Zeiss SMT")).isEmpty();
        server.verify(); // no request was made
    }


    @Test
    void searchesAgainWithoutUnitWordsWhenTheFirstDomainIsOnlyTheNameRunTogether() {
        LogoDomainClient client = new LogoDomainClient(builder, "sk_test");
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                [{"name": "Carl Zeiss SMT", "domain": "carlzeisssmt.com"}]""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SHORTER_URL)).andRespond(withSuccess("""
                [{"name": "Example Zeiss", "domain": "zeiss.example"}]""", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Carl Zeiss SMT")).contains("zeiss.example");
        server.verify();
    }

    @Test
    void searchesAgainWithoutUnitWordsWhenTheFirstSearchFindsNothing() {
        LogoDomainClient client = new LogoDomainClient(builder, "sk_test");
        server.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SHORTER_URL)).andRespond(withSuccess("""
                [{"name": "Example Zeiss", "domain": "zeiss.example"}]""", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Carl Zeiss SMT")).contains("zeiss.example");
        server.verify();
    }

    @Test
    void keepsTheFirstDomainWhenTheSecondSearchFindsNothing() {
        LogoDomainClient client = new LogoDomainClient(builder, "sk_test");
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                [{"name": "Carl Zeiss SMT", "domain": "carlzeisssmt.com"}]""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SHORTER_URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Carl Zeiss SMT")).contains("carlzeisssmt.com");
        server.verify();
    }

    @Test
    void asksOnceForAOneWordNameEvenWhenItsDomainIsTheNameRunTogether() {
        LogoDomainClient client = new LogoDomainClient(builder, "sk_test");
        server.expect(requestTo("https://api.logo.dev/search?q=Fabrinet")).andRespond(withSuccess("""
                [{"name": "Fabrinet", "domain": "fabrinet.example"}]""", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Fabrinet")).contains("fabrinet.example");
        server.verify(); // exactly one request
    }

    @Test
    void dropsLegalEndingsAndShortWordsInCapitalsButNeverEmptiesTheName() {
        assertThat(LogoDomainClient.withoutUnitWords("Carl Zeiss SMT")).isEqualTo("Carl Zeiss");
        assertThat(LogoDomainClient.withoutUnitWords("Samsung Electronics Co., Ltd.")).isEqualTo("Samsung Electronics");
        assertThat(LogoDomainClient.withoutUnitWords("Systems on Silicon Manufacturing Company Pte. Ltd."))
                .isEqualTo("Systems on Silicon Manufacturing");
        assertThat(LogoDomainClient.withoutUnitWords("SK Hynix")).isEqualTo("SK Hynix");
        assertThat(LogoDomainClient.withoutUnitWords("TSMC")).isEqualTo("TSMC");
    }

    @Test
    void recognisesADomainThatIsOnlyTheNameRunTogether() {
        assertThat(LogoDomainClient.isNameRunTogether("carlzeisssmt.com", "Carl Zeiss SMT")).isTrue();
        assertThat(LogoDomainClient.isNameRunTogether("zeiss.com", "Carl Zeiss SMT")).isFalse();
    }
}
