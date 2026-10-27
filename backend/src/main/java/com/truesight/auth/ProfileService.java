package com.truesight.auth;

import com.truesight.common.ApiException;
import com.truesight.user.CurrentUser;
import com.truesight.user.User;
import com.truesight.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a logged-in user can change about their own account: the name shown instead of their email, and their
 * password. The email cannot be changed. Both act on whoever the token belongs to, never on an id from the request.
 */
@Service
public class ProfileService {

    /** The longest display name the {@code users} table accepts. */
    public static final int MAX_NAME = 100;

    static final String WRONG_CURRENT_PASSWORD = "Your current password is not right.";
    static final String BAD_NAME = "displayName must be between 1 and " + MAX_NAME + " characters";

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final CurrentUser currentUser;

    public ProfileService(UserRepository users, PasswordEncoder passwords, CurrentUser currentUser) {
        this.users = users;
        this.passwords = passwords;
        this.currentUser = currentUser;
    }

    /** Saves the name to show instead of the email: 1 to 100 characters once trimmed, else a 400. */
    @Transactional
    public User setDisplayName(String displayName) {
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw ApiException.badRequest(BAD_NAME);
        }
        User user = signedInUser();
        user.setDisplayName(name);
        return users.save(user);
    }

    /**
     * Changes the password, when the current one is right; otherwise a 400 and nothing changes. The token the user
     * logged in with stays valid, so they stay logged in.
     */
    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        User user = signedInUser();
        if (user.getPasswordHash() == null || !passwords.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest(WRONG_CURRENT_PASSWORD);
        }
        user.setPasswordHash(passwords.encode(newPassword));
        users.save(user);
    }

    private User signedInUser() {
        return users.findById(currentUser.id()).orElseThrow(() -> ApiException.unauthorized("Please log in."));
    }
}
