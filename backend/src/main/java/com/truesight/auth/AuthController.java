package com.truesight.auth;

import com.truesight.common.ApiException;
import com.truesight.user.CurrentUser;
import com.truesight.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign up, log in, and "who am I?".
 *
 * <p>Logging out has no endpoint: the token lives only in the browser, so the website simply forgets it.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Accounts")
public class AuthController {

    private final AuthService auth;
    private final CurrentUser currentUser;
    private final UserRepository users;

    public AuthController(AuthService auth, CurrentUser currentUser, UserRepository users) {
        this.auth = auth;
        this.currentUser = currentUser;
        this.users = users;
    }

    /** What a new account needs. The password rules are {@link NewPassword}, the same as Change Password uses. */
    public record SignUpRequest(@NotBlank @Email String email, @NewPassword String password) {
    }

    public record LogInRequest(@NotBlank String email, @NotBlank String password) {
    }

    /**
     * The answer to signing up or logging in. The website keeps the token and sends it with every request. {@code role}
     * is {@code PORTFOLIO_MANAGER} or {@code ANALYST}.
     */
    public record AuthResponse(String token, String email, String role) {
    }

    /**
     * Who is logged in. {@code role} is {@code PORTFOLIO_MANAGER} or {@code ANALYST}. {@code displayName} is the name
     * the user chose to be shown instead of their email, or null while they have not set one.
     */
    public record MeResponse(String email, String role, String displayName) {
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account, and log in to it")
    public AuthResponse signUp(@Valid @RequestBody SignUpRequest request) {
        AuthService.LoggedIn loggedIn = auth.signUp(request.email(), request.password());
        return new AuthResponse(loggedIn.token(), loggedIn.email(), loggedIn.role());
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with an email and password")
    public AuthResponse logIn(@Valid @RequestBody LogInRequest request) {
        AuthService.LoggedIn loggedIn = auth.logIn(request.email(), request.password());
        return new AuthResponse(loggedIn.token(), loggedIn.email(), loggedIn.role());
    }

    @GetMapping("/me")
    @Operation(summary = "Show who is logged in")
    public MeResponse me() {
        return users.findById(currentUser.id())
                .map(user -> new MeResponse(user.getEmail(), user.getRole().name(), user.getDisplayName()))
                .orElseThrow(() -> ApiException.unauthorized("Please log in."));
    }
}
