package com.truesight.common;

/**
 * What every endpoint sends back when something goes wrong: a JSON object with one field, {@code message}, written
 * for a person to read, e.g. {@code {"message": "The file needs a ticker column."}}.
 *
 * <p>Having one shape everywhere means the website shows every error the same way, with no special cases.
 */
public record ApiError(String message) {
}
