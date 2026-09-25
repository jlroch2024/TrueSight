package com.truesight.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Who may reach what, and how a login is proved.
 *
 * <p>Logging in gives the browser a <b>token</b>: a short piece of text, signed with a secret only the backend knows,
 * that says "this is user 7, valid for 2 hours". The browser sends it with every request. Because it is signed, the
 * backend can trust it without storing anything, so there are no sessions.
 *
 * <p>The rules:
 * <ul>
 *   <li>Health, sign up and log in are open to anybody.</li>
 *   <li>Every other {@code /api/} address needs a valid token. Without one, the answer is 401 with
 *       {@code {"message": "Please log in."}}, the same shape as every other error.</li>
 *   <li>Everything outside {@code /api/} is open: the website itself, and the Swagger page.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** The signing algorithm needs a secret of at least 256 bits: 32 characters. */
    private static final int MIN_SECRET_LENGTH = 32;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint pleaseLogIn = (request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Please log in.\"}");
        };

        http
                // CSRF protection is for sites that log in with cookies. We send the token in a header instead,
                // which a different website cannot do on the user's behalf, so it is not needed.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers("/api/health", "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(tokens -> tokens
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(pleaseLogIn))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(pleaseLogIn));
        return http.build();
    }

    /**
     * The secret tokens are signed with, from {@code JWT_SECRET}. On a laptop with no secret set, a random one is made
     * at start-up, so nothing needs setting up; logins then last only until the backend restarts.
     */
    @Bean
    SecretKey tokenSigningKey(@Value("${truesight.jwt-secret:}") String secret) {
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            bytes = new byte[MIN_SECRET_LENGTH];
            new SecureRandom().nextBytes(bytes);
            log.warn("JWT_SECRET is not set: using a random one. Logins end when the backend restarts.");
        } else if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_LENGTH + " characters.");
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey tokenSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(tokenSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey tokenSigningKey) {
        return NimbusJwtDecoder.withSecretKey(tokenSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Scrambles passwords before they are saved. BCrypt is slow on purpose, so stolen hashes are hard to crack. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
