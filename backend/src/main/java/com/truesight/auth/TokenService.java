package com.truesight.auth;

import com.truesight.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Makes the token a user gets when they sign up or log in.
 *
 * <p>The token says who the user is (their id, as the "subject") and when it stops working (2 hours from now). It is
 * signed with the backend's secret, so it cannot be changed or forged. {@link com.truesight.user.CurrentUser} reads
 * the id back out of it on every request.
 */
@Service
public class TokenService {

    /** How long a login lasts. After that, the user logs in again. */
    public static final Duration LIFETIME = Duration.ofHours(2);

    private final JwtEncoder encoder;

    public TokenService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("truesight")
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(LIFETIME))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
