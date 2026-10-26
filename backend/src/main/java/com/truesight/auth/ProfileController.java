package com.truesight.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Edit my profile: the display name shown instead of the email, and the password. The email cannot be changed. */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Profile")
public class ProfileController {

    private final ProfileService profile;

    public ProfileController(ProfileService profile) {
        this.profile = profile;
    }

    /** The name to show instead of the email: 1 to 100 characters. */
    public record DisplayNameRequest(
            @NotBlank(message = "must not be blank")
            @Size(max = ProfileService.MAX_NAME, message = "must be at most 100 characters") String displayName) {
    }

    /** The current password to prove it is them, and a new one that follows the same rules as Sign Up. */
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NewPassword String newPassword) {
    }

    @PutMapping
    @Operation(summary = "Change my display name")
    public AuthController.MeResponse changeDisplayName(@Valid @RequestBody DisplayNameRequest request) {
        var user = profile.setDisplayName(request.displayName());
        return new AuthController.MeResponse(user.getEmail(), user.getRole().name(), user.getDisplayName());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change my password; I stay logged in")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profile.changePassword(request.currentPassword(), request.newPassword());
    }
}
