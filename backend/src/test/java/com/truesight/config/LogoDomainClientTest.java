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
 * are Logo.dev's real ones for these names, retrieved on 20 October 2026, cut to their first brands' name and domain.
 */
class LogoDomainClientTest {

    private static final String SEARCH = "https://api.logo.dev/search?q=";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final LogoDomainClient client = new LogoDomainClient(builder, "sk_test");

    @Test
    void searchesTheNameWithoutItsLegalEndingWithTheSecretKey() {
        server.expect(requestTo(SEARCH + "Amkor%20Technology"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer sk_test"))
                .andRespond(withSuccess("""
                        [{"name": "Amkor Technology", "domain": "amkor.com"},
                         {"name": "Amkortechnology", "domain": "amkortechnology.com"}]""", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Amkor Technology, Inc.")).contains("amkor.com");
        server.verify();
    }

    @Test
    void takesTheBrandWhoseNameIsTheCompanysNotTheFirstOne() {
        server.expect(requestTo(SEARCH + "Panasonic")).andRespond(withSuccess("""
                [{"name": "Panasonic North America", "domain": "panasonic.com"},
                 {"name": "Panasonic Avionics Corporation", "domain": "panasonic.aero"},
                 {"name": "Panasonic", "domain": "holdings.panasonic"}]""", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Panasonic")).contains("holdings.panasonic");
    }

    @Test
    void refusesABrandThatSharesOnlyOneWordWithTheCompany() {
        // Logo.dev's first answer for King Yuan Electronics is a restaurant called Yuan.
        server.expect(requestTo(SEARCH + "King%20Yuan%20Electronics")).andRespond(withSuccess("""
                [{"name": "Yuan", "domain": "yuanhotpot.com"},
                 {"name": "Yuan", "domain": "yuanjiangpc.com"}]""", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("King Yuan Electronics Company")).isEmpty();
    }

    @Test
    void refusesTheEntryLogoDevMakesUpFromAnUnknownName() {
        server.expect(requestTo(SEARCH + "Hon%20Hai%20Precision%20Industry")).andRespond(withSuccess("""
                [{"name": "honhaiprecisionindustry.com", "domain": "honhaiprecisionindustry.com"}]""",
                MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Hon Hai Precision Industry Co., Ltd.")).isEmpty();
    }

    @Test
    void searchesWithoutUnitWordsAndRefusesBrandsThatAreOnlyPartOfTheName() {
        // "Carl" is the Carl Zeiss foundation's site, and Carl Zeiss Sports Optics now belongs to another company.
        server.expect(requestTo(SEARCH + "Carl%20Zeiss")).andRespond(withSuccess("""
                [{"name": "Carl", "domain": "carl-zeiss-stiftung.de"},
                 {"name": "Carl Zeiss Sports Optics", "domain": "hensoldt.de"},
                 {"name": "Carl Zeiss MES Solutions", "domain": "guardus-solutions.de"}]""", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Carl Zeiss SMT")).isEmpty();
        server.verify();
    }

    @Test
    void refusesABrandNamedByOnlyTheFirstWordEvenWhenItIsTheRightCompany() {
        // Siliconware is right here, but "the first word" would also accept "King" for King Yuan Electronics: the rule
        // stays strict, and Siliconware Precision Industries shows its letter.
        server.expect(requestTo(SEARCH + "Siliconware%20Precision%20Industries")).andRespond(withSuccess("""
                [{"name": "Siliconware", "domain": "siliconware.com"},
                 {"name": "Precision Industries", "domain": "precind.com"}]""", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Siliconware Precision Industries Ltd.")).isEmpty();
    }

    @Test
    void dropsOpCoAsALegalEnding() {
        server.expect(requestTo(SEARCH + "OpenAI")).andRespond(withSuccess("""
                [{"name": "OpenAI", "domain": "openai.com"},
                 {"name": "OpenAirlines", "domain": "openairlines.com"}]""", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("OpenAI OpCo, LLC")).contains("openai.com");
    }

    @Test
    void findsNothingWhenNoBrandMatches() {
        server.expect(requestTo(SEARCH + "Fabrinet")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.domainOf("Fabrinet")).isEmpty();
    }

    @Test
    void findsNothingWhenLogoDevFails() {
        server.expect(requestTo(SEARCH + "Fabrinet")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.domainOf("Fabrinet")).isEmpty();
    }

    @Test
    void asksNothingWithoutASecretKey() {
        LogoDomainClient noKey = new LogoDomainClient(builder, "  ");

        assertThat(noKey.domainOf("Fabrinet")).isEmpty();
        server.verify(); // no request was made
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
}
