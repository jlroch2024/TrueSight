package com.truesight.auth;

import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Acceptance Criteria of Sign Up and Log In, through the whole app and a real database. Each test uses its own
 * email, so tests never affect each other.
 */
@IntegrationTest
class AuthIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestLogins logins;

    private final JsonMapper json = JsonMapper.builder().build();

    private String signUp(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aNewUserCanSignUpAndIsThenLoggedIn() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"long-enough\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void aUserCanLogInWithTheRightPassword() throws Exception {
        signUp("login@example.com", "right-password");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@example.com\",\"password\":\"right-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void theLocalAnalystDemoAccountHasTheAnalystRole() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"analyst@truesight.local\",\"password\":\"truesight-demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String token = json.readTree(login.getResponse().getContentAsString()).get("token").asText();
        String claimsJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(json.readTree(claimsJson).get("role").asText()).isEqualTo("ANALYST");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ANALYST"));
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailGiveTheSameMessage() throws Exception {
        signUp("wrong@example.com", "right-password");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrong@example.com\",\"password\":\"not-the-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong email or password"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"anything-at-all\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong email or password"));
    }

    @Test
    void signingUpWithAnEmailThatAlreadyHasAnAccountIsRefused() throws Exception {
        signUp("taken@example.com", "first-password");

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Taken@Example.com\",\"password\":\"second-password\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This email is already registered"));
    }

    @Test
    void aPasswordShorterThan8CharactersIsRefusedWithAMessageSayingSo() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"short@example.com\",\"password\":\"1234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must be between 8 and 72 characters"));
    }

    @Test
    void somethingThatIsNotAnEmailIsRefused() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"long-enough\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("email")));
    }

    @Test
    void withoutATokenTheBackendRefusesRequests() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please log in."));
    }

    @Test
    void aForgedTokenIsRefused() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withATokenTheBackendKnowsWhoIsLoggedIn() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", logins.bearer("me@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("PORTFOLIO_MANAGER"));
    }

    @Test
    void roleChangesTakeEffectForAnAlreadyIssuedTokenOnTheNextRequest() throws Exception {
        String bearer = logins.bearer("role-change@example.com");
        logins.analystBearer("role-change@example.com");
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ANALYST"));
    }

    @Test
    void noAnswerEverContainsThePasswordOrItsHash() throws Exception {
        String answer = signUp("secret@example.com", "my-secret-password");

        assertThat(answer).doesNotContain("my-secret-password").doesNotContain("$2a$");
        mockMvc.perform(get("/api/auth/me").header("Authorization", logins.bearer("secret@example.com")))
                .andExpect(content().string(not(containsString("$2a$"))));
    }
}
