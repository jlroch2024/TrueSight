package com.truesight.support;

import com.truesight.auth.TokenService;
import com.truesight.user.User;
import com.truesight.user.UserRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Makes requests as a logged-in user in integration tests. Every {@code @IntegrationTest} class can use it:
 *
 * <pre>
 *     &#64;Autowired TestLogins logins;
 *
 *     mockMvc.perform(get("/api/portfolios").header("Authorization", logins.bearer("pm@example.com")))
 * </pre>
 *
 * <p>The user is created the first time an email is used, with the password {@value #PASSWORD}. Use a different
 * email in each test, so tests never see each other's data.
 */
@TestComponent
public class TestLogins {

    public static final String PASSWORD = "test-password";

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    public TestLogins(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    /** The user with this email, created if needed. */
    public User user(String email) {
        return users.findByEmail(email)
                .orElseGet(() -> users.save(new User(email, passwords.encode(PASSWORD))));
    }

    /** "Bearer ..." for this user: the value of an Authorization header that logs the request in. */
    public String bearer(String email) {
        return "Bearer " + tokens.issue(user(email));
    }
}
