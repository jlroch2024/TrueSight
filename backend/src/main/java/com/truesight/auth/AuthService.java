package com.truesight.auth;

import com.truesight.common.ApiException;
import com.truesight.user.AnalystEmails;
import com.truesight.user.Role;
import com.truesight.user.User;
import com.truesight.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Signing up and logging in.
 *
 * <p>Emails are compared ignoring capitals and spaces around them, so "Jo@Example.com " and "jo@example.com" are the
 * same account. Passwords are only ever stored scrambled (see {@code SecurityConfig#passwordEncoder}).
 */
@Service
public class AuthService {

    /** The same message for a wrong email and a wrong password, so nobody can find out which emails have accounts. */
    static final String WRONG_LOGIN = "Wrong email or password.";

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final AnalystEmails analystEmails;

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens,
                       AnalystEmails analystEmails) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.analystEmails = analystEmails;
    }

    /** What signing up or logging in gives back: the token, the email it belongs to, and that user's role. */
    public record LoggedIn(String token, String email, String role) {
    }

    @Transactional
    public LoggedIn signUp(String email, String password) {
        String normalised = normalise(email);
        if (users.existsByEmail(normalised)) {
            throw ApiException.conflict("This email is already registered.");
        }
        User newUser = new User(normalised, passwords.encode(password));
        // Nobody picks their own role: only the emails in the ANALYST_EMAILS setting become Analysts.
        newUser.setRole(analystEmails.isAnalyst(normalised) ? Role.ANALYST : Role.PORTFOLIO_MANAGER);
        User user = users.save(newUser);
        return new LoggedIn(tokens.issue(user), user.getEmail(), user.getRole().name());
    }

    public LoggedIn logIn(String email, String password) {
        User user = users.findByEmail(normalise(email))
                .filter(found -> found.getPasswordHash() != null)
                .filter(found -> passwords.matches(password, found.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized(WRONG_LOGIN));
        return new LoggedIn(tokens.issue(user), user.getEmail(), user.getRole().name());
    }

    static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
