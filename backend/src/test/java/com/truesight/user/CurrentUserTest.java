package com.truesight.user;

import com.truesight.common.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code requireAnalyst()}, the one check every Analyst-only endpoint starts with. */
class CurrentUserTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUser currentUser = new CurrentUser(users);

    @AfterEach
    void forgetTheLogin() {
        SecurityContextHolder.clearContext();
    }

    /** Logs in as a user with this id and role. The role is read from the database, as in the real app. */
    private void logInAs(long id, Role role) {
        User user = new User("someone@example.com", null);
        user.setRole(role);
        when(users.findById(id)).thenReturn(Optional.of(user));
        Jwt token = Jwt.withTokenValue("token").header("alg", "HS256").subject(String.valueOf(id)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
    }

    @Test
    void aPortfolioManagerIsRefusedWithA403AndTheAgreedMessage() {
        logInAs(5L, Role.PORTFOLIO_MANAGER);

        assertThatThrownBy(currentUser::requireAnalyst)
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getMessage()).isEqualTo("Only analysts can do this.");
                });
    }

    @Test
    void anAnalystIsLetThrough() {
        logInAs(6L, Role.ANALYST);

        assertThatCode(currentUser::requireAnalyst).doesNotThrowAnyException();
    }

    @Test
    void whenNobodyIsLoggedInTheAnswerIsA401NotA403() {
        assertThatThrownBy(currentUser::requireAnalyst)
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
