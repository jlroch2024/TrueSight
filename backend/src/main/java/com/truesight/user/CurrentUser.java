package com.truesight.user;

import com.truesight.common.ApiException;
import org.springframework.stereotype.Component;

/**
 * Answers "who is logged in?". Every endpoint that needs to know asks this, and nothing else.
 *
 * <p><b>This is a placeholder.</b> Until the Sign Up and Log In story is merged, it always answers with the demo user
 * (see {@link DemoUserSeeder}), so the portfolio, upload and report stories can be built and tested before real
 * logins exist. That story replaces the inside of this class with the real logged-in user, taken from the sign-in
 * token. Nothing that calls it has to change.
 *
 * <p>On the live site there is no demo user, so until then this answers "please log in".
 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    /** The logged-in user's id. Throws a 401 if nobody is logged in. */
    public Long id() {
        return users.findByEmail(DemoUserSeeder.DEMO_EMAIL)
                .map(User::getId)
                .orElseThrow(() -> ApiException.unauthorized("Please log in."));
    }
}
