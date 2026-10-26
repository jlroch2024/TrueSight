package com.truesight.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import com.truesight.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Acceptance Criteria of Edit My Profile, through the whole app and a real database. Each test uses its own email,
 * so tests never see each other's accounts.
 */
@IntegrationTest
class ProfileIT {

    private static final String WRONG_CURRENT = "Your current password is not right.";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired UserRepository users;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;

    private final JsonMapper json = JsonMapper.builder().build();

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static String email() {
        return "profile-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions me(String bearer) throws Exception {
        return mockMvc.perform(get("/api/auth/me").header("Authorization", bearer));
    }

    /** PUT /api/me with a body as written. */
    private ResultActions putMe(String bearer, String body) throws Exception {
        return mockMvc.perform(put("/api/me").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** PUT /api/me asking for this display name. */
    private ResultActions setName(String bearer, String name) throws Exception {
        return putMe(bearer, body("displayName", name));
    }

    private ResultActions changePassword(String bearer, String current, String next) throws Exception {
        return mockMvc.perform(put("/api/me/password").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("currentPassword", current, "newPassword", next)));
    }

    private ResultActions logIn(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body("email", email, "password", password)));
    }

    /** A JSON body from name, value, name, value... */
    private String body(Object... pairs) throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) fields.put((String) pairs[i], pairs[i + 1]);
        return json.writeValueAsString(fields);
    }

    /** A relationship waiting for an Analyst's decision. */
    private Relationship relationship() {
        Company holding = companyDirectory.findOrCreate("Profile Holding " + UUID.randomUUID(), null, "PRH", List.of());
        Company supplier = companyDirectory.findOrCreate("Profile Supplier " + UUID.randomUUID(), null, "PRS", List.of());
        Report report = reports.save(new Report(holding.getId(), "10-K",
                "pf-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/" + UUID.randomUUID(), "text"));
        return relationships.save(new Relationship(report.getId(), holding.getId(), supplier.getId(),
                RelationshipType.SUPPLIER, "semiconductor wafers", "We buy wafers from them."));
    }

    private ResultActions decide(String bearer, long relationshipId, String decision) throws Exception {
    // A rejection must say why, so every decision here carries a note.
    return mockMvc.perform(put("/api/relationships/{id}/review", relationshipId).header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\":\"" + decision + "\",\"note\":\"Checked in the profile test.\"}"));
    }

    // ---- criterion 1: saving a display name shows it ------------------------------------------------------------

    @Test
    void aUserWithoutADisplayNameHasNullInTheirAccountAnswer() throws Exception {
        me(logins.bearer(email()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void savingADisplayNameAnswersWithTheAccountAndShowsItFromThenOn() throws Exception {
        String email = email();
        String bearer = logins.bearer(email);

        setName(bearer, "Jo Tan")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("PORTFOLIO_MANAGER"))
                .andExpect(jsonPath("$.displayName").value("Jo Tan"));

        me(bearer).andExpect(jsonPath("$.displayName").value("Jo Tan")).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void theNameIsTrimmedAndMayBeChangedAgain() throws Exception {
        String bearer = logins.bearer(email());

        setName(bearer, "  Jo Tan  ").andExpect(jsonPath("$.displayName").value("Jo Tan"));
        setName(bearer, "Joanna Tan").andExpect(jsonPath("$.displayName").value("Joanna Tan"));

        me(bearer).andExpect(jsonPath("$.displayName").value("Joanna Tan"));
    }

    @Test
    void aNameOfExactlyOneOrOneHundredCharactersIsAccepted() throws Exception {
        String bearer = logins.bearer(email());

        setName(bearer, "J").andExpect(status().isOk());
        setName(bearer, "x".repeat(100)).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("x".repeat(100)));
    }

    @Test
    void anAnalystsDecisionShowsTheirDisplayNameOnceSetAndTheirEmailBefore() throws Exception {
        String email = email();
        String bearer = logins.analystBearer(email);
        Relationship before = relationship();
        Relationship after = relationship();

        decide(bearer, before.getId(), "APPROVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedBy").value(email));

        setName(bearer, "Jo Tan").andExpect(status().isOk());
        decide(bearer, after.getId(), "APPROVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedBy").value("Jo Tan"));

        // The earlier decision is shown by the name too, as the name belongs to the person, not to the decision.
        String list = mockMvc.perform(get("/api/reviews?status=APPROVED").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode items = json.readTree(list);
        for (JsonNode item : items) {
            long id = item.path("relationship").path("id").asLong();
            if (id == before.getId() || id == after.getId()) {
                assertThat(item.path("reviewedBy").asText()).isEqualTo("Jo Tan");
            }
        }
    }

    @Test
    void aRejectionAlsoShowsTheDisplayName() throws Exception {
        String bearer = logins.analystBearer(email());
        setName(bearer, "Jo Tan").andExpect(status().isOk());

        decide(bearer, relationship().getId(), "REJECTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedBy").value("Jo Tan"));
    }

    // ---- criterion 2: a blank or over-long name is refused with 400 ---------------------------------------------

    @Test
    void aBlankDisplayNameIsRefusedWith400AndTheOldNameStays() throws Exception {
        String bearer = logins.bearer(email());
        setName(bearer, "Jo Tan").andExpect(status().isOk());

        setName(bearer, "").andExpect(status().isBadRequest());
        setName(bearer, "   ").andExpect(status().isBadRequest());
        putMe(bearer, "{\"displayName\":null}").andExpect(status().isBadRequest());
        putMe(bearer, "{}").andExpect(status().isBadRequest());

        me(bearer).andExpect(jsonPath("$.displayName").value("Jo Tan"));
    }

    @Test
    void anOverLongDisplayNameIsRefusedWith400AndTheOldNameStays() throws Exception {
        String bearer = logins.bearer(email());
        setName(bearer, "Jo Tan").andExpect(status().isOk());

        setName(bearer, "x".repeat(101))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("displayName")));

        me(bearer).andExpect(jsonPath("$.displayName").value("Jo Tan"));
    }

    @Test
    void theEmailCannotBeChanged() throws Exception {
        String email = email();
        String bearer = logins.bearer(email);

        putMe(bearer, body("displayName", "Jo Tan", "email", "someone-else@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        me(bearer).andExpect(jsonPath("$.email").value(email));
        assertThat(users.findByEmail("someone-else@example.com")).isEmpty();
    }

    @Test
    void aUserOnlyEverChangesTheirOwnName() throws Exception {
        String mine = email();
        String theirs = email();
        String myBearer = logins.bearer(mine);
        String theirBearer = logins.bearer(theirs);

        setName(myBearer, "Jo Tan").andExpect(status().isOk());

        me(theirBearer).andExpect(jsonPath("$.displayName").value(org.hamcrest.Matchers.nullValue()));
    }

    // ---- criterion 3: the new password logs in, the old one no longer does --------------------------------------

    @Test
    void changingThePasswordWithTheRightCurrentOneWorksTheNewOneLogsInAndTheOldOneNoLongerDoes() throws Exception {
        String email = email();
        String bearer = logins.bearer(email);

        changePassword(bearer, TestLogins.PASSWORD, "a-brand-new-password")
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        logIn(email, "a-brand-new-password").andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.token").isNotEmpty());
        logIn(email, TestLogins.PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void theUserStaysLoggedInAfterChangingThePassword() throws Exception {
        String bearer = logins.bearer(email());

        changePassword(bearer, TestLogins.PASSWORD, "a-brand-new-password").andExpect(status().isNoContent());

        me(bearer).andExpect(status().isOk());
    }

    @Test
    void thePasswordIsStoredScrambledNotAsTyped() throws Exception {
        String email = email();
        changePassword(logins.bearer(email), TestLogins.PASSWORD, "a-brand-new-password")
                .andExpect(status().isNoContent());

        String hash = users.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo("a-brand-new-password").startsWith("$2");
    }

    @Test
    void thePasswordCanBeChangedAgainWithTheNewOne() throws Exception {
        String email = email();
        String bearer = logins.bearer(email);

        changePassword(bearer, TestLogins.PASSWORD, "second-password").andExpect(status().isNoContent());
        changePassword(bearer, "second-password", "third-password").andExpect(status().isNoContent());

        logIn(email, "third-password").andExpect(status().is2xxSuccessful());
        logIn(email, "second-password").andExpect(status().isUnauthorized());
    }

    // ---- criterion 4: a wrong current password is refused with 400 and the message ------------------------------

    @Test
    void aWrongCurrentPasswordIsRefusedWith400AndTheMessageAndNothingChanges() throws Exception {
        String email = email();
        String bearer = logins.bearer(email);

        changePassword(bearer, "not-my-password", "a-brand-new-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(WRONG_CURRENT));

        logIn(email, TestLogins.PASSWORD).andExpect(status().is2xxSuccessful());
        logIn(email, "a-brand-new-password").andExpect(status().isUnauthorized());
        me(bearer).andExpect(status().isOk());
    }

    @Test
    void aNewPasswordThatBreaksTheSignUpRulesIsRefusedWith400() throws Exception {
        String email = email();
        String bearer = logins.bearer(email);

        changePassword(bearer, TestLogins.PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("must be between 8 and 72 characters")));
        changePassword(bearer, TestLogins.PASSWORD, "x".repeat(73)).andExpect(status().isBadRequest());
        changePassword(bearer, TestLogins.PASSWORD, "          ").andExpect(status().isBadRequest());
        changePassword(bearer, TestLogins.PASSWORD, "x".repeat(72)).andExpect(status().isNoContent());
    }

    @Test
    void theSameRulesAreUsedBySignUp() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", email(), "password", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must be between 8 and 72 characters"));
    }

    @Test
    void aMissingCurrentOrNewPasswordIsRefusedWith400() throws Exception {
        String bearer = logins.bearer(email());

        mockMvc.perform(put("/api/me/password").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"a-brand-new-password\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/me/password").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"" + TestLogins.PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theDemoUserWhoHasNoPasswordCannotPassTheCurrentPasswordCheck() throws Exception {
        String email = email();
        com.truesight.user.User user = logins.user(email);
        user.setPasswordHash(null);
        users.save(user);

        changePassword(logins.bearer(email), "anything-at-all", "a-brand-new-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(WRONG_CURRENT));
    }

    // ---- login needed --------------------------------------------------------------------------------------------

    @Test
    void bothChangesNeedALogin() throws Exception {
        mockMvc.perform(put("/api/me").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Jo\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content(body("currentPassword", "x", "newPassword", "a-brand-new-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noAnswerContainsThePasswordOrItsHash() throws Exception {
        String bearer = logins.bearer(email());

        String answer = setName(bearer, "Jo Tan").andReturn().getResponse().getContentAsString();

        assertThat(answer).doesNotContain(TestLogins.PASSWORD).doesNotContain("$2a$").doesNotContain("passwordHash");
    }
}
