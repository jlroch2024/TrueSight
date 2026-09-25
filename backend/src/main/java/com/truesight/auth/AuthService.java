package com.truesight.auth;

import com.truesight.common.ApiException;
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
    static final String WRONG_LOGIN = "Wrong email or password";

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    /** What signing up or logging in gives back: the token, and the email it belongs to. */
    public record LoggedIn(String token, String email) {
    }

    @Transactional
    public LoggedIn signUp(String email, String password) {
        String normalised = normalise(email);
        if (users.existsByEmail(normalised)) {
            throw ApiException.conflict("This email is already registered");
        }
        User user = users.save(new User(normalised, passwords.encode(password)));
        return new LoggedIn(tokens.issue(user), user.getEmail());
    }

    public LoggedIn logIn(String email, String password) {
        User user = users.findByEmail(normalise(email))
                .filter(found -> found.getPasswordHash() != null)
                .filter(found -> passwords.matches(password, found.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized(WRONG_LOGIN));
        return new LoggedIn(tokens.issue(user), user.getEmail());
    }

    static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
