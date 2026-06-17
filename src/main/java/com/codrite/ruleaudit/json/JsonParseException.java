package com.codrite.ruleaudit.json;

/**
 * Exception thrown when a JSON payload cannot be parsed by the {@link JsonContextFactory}.
 */
public class JsonParseException extends RuntimeException {
    /**
     * @param message The error message.
     * @param cause   The underlying cause (usually a Jackson exception).
     */
    public JsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
