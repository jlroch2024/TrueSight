package com.truesight.user;

import com.truesight.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Answers "who is logged in?". Every endpoint that needs to know asks this, and nothing else.
 *
 * <p>It reads the answer from the token the browser sent. Spring Security has already checked the token's signature
 * and expiry before any endpoint runs, so the id inside it can be trusted. The token's "subject" is the user's id
 * (see {@link com.truesight.auth.TokenService}).
 */
@Component
public class CurrentUser {

    /** The logged-in user's id. Throws a 401 if nobody is logged in. */
    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return Long.valueOf(token.getToken().getSubject());
        }
        throw ApiException.unauthorized("Please log in.");
    }
}
