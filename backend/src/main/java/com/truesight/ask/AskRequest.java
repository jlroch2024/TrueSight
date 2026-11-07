package com.truesight.ask;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A question about one portfolio, with the conversation so far. The website keeps the conversation in the browser tab
 * and sends back the last 6 questions and answers, so the question can refer to them ("and the second one?").
 */
public record AskRequest(
        @NotBlank(message = "is needed.")
        @Size(max = 500, message = "must be 500 characters or fewer.") String question,
        @Size(max = 6, message = "must be 6 turns or fewer.") List<@Valid Turn> history) {

    public AskRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    /** One earlier question and the answer it was given. */
    public record Turn(
            @NotBlank(message = "is needed.")
            @Size(max = 500, message = "must be 500 characters or fewer.") String question,
            @NotBlank(message = "is needed.")
            @Size(max = 4000, message = "must be 4000 characters or fewer.") String answer) {
    }
}
