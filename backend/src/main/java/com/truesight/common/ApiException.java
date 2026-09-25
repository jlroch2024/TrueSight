package com.truesight.common;

import org.springframework.http.HttpStatus;

/**
 * Throw this from anywhere in the backend to stop and send the user an error. {@link GlobalExceptionHandler} turns it
 * into the right HTTP status and an {@link ApiError}, so no endpoint builds error responses itself.
 *
 * <pre>
 *     throw ApiException.notFound("Portfolio not found.");
 *     throw ApiException.badRequest("The file needs a ticker column.");
 * </pre>
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /** 400: the request itself is wrong, e.g. a missing field or a bad file. */
    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /** 401: nobody is logged in. */
    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    /**
     * 404: the thing does not exist, OR it belongs to another user. Both cases give the same answer on purpose, so
     * nobody can find out which portfolios exist by trying numbers.
     */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    /** 409: the request clashes with what is already there, e.g. a portfolio name already in use. */
    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
