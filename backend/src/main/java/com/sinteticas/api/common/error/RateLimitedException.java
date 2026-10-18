package com.sinteticas.api.common.error;

import org.springframework.http.HttpStatus;

import java.time.Duration;

/**
 * 429 Too Many Requests con el header {@code Retry-After} (segundos que el cliente debe esperar).
 */
public class RateLimitedException extends ApiException {

    private final Duration retryAfter;

    public RateLimitedException(String code, String message, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

}
