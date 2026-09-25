package com.truesight.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * The one place every error in the backend is turned into a response.
 *
 * <p>Spring calls these methods whenever an endpoint throws. Each one picks the HTTP status and a message a person
 * can read, and sends it as an {@link ApiError}. Endpoints never catch errors just to build a response.
 *
 * <p>A new kind of error that needs its own status and message gets its own method here, not a try/catch in an
 * endpoint.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Our own errors, thrown on purpose with {@link ApiException}. */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handle(ApiException e) {
        return respond(e.status(), e.getMessage());
    }

    /** A request field broke a rule such as @NotBlank or @Size. Lists every broken rule. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handle(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, message.isEmpty() ? "The request is not valid." : message);
    }

    /** The body was not valid JSON, or had the wrong shape. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handle(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "The request body could not be read.");
    }

    /** An upload over the size limit in application.yml. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handle(MaxUploadSizeExceededException e) {
        return respond(HttpStatus.CONTENT_TOO_LARGE, "The file is too large.");
    }

    /** An address that does not exist. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handle(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "Not found.");
    }

    /** A real address, called the wrong way, e.g. DELETE on an address that only allows GET. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handle(HttpRequestMethodNotSupportedException e) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "This action is not allowed here.");
    }

    /**
     * Anything else is a bug. The details go to the log for us to read; the user gets a plain message, because error
     * details can reveal how the system works inside.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handle(Exception e) {
        log.error("Unexpected error", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
