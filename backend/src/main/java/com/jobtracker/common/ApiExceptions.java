package com.jobtracker.common;

/** Domain exceptions mapped to HTTP statuses by {@link GlobalExceptionHandler}. */
public final class ApiExceptions {

    private ApiExceptions() {}

    /** 404 - also used when a resource belongs to someone else, so ids cannot be probed. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /** 409 */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    /** 429 */
    public static class RateLimitException extends RuntimeException {
        private final long retryAfterSeconds;

        public RateLimitException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** 503 - the AI provider is not configured or not reachable. */
    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message) {
            super(message);
        }
    }

    /** 422 - the AI answered, but there was nothing usable in the input. */
    public static class AiParseException extends RuntimeException {
        public AiParseException(String message) {
            super(message);
        }
    }
}
