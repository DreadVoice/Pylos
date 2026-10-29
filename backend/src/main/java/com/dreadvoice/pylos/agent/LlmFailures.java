package com.dreadvoice.pylos.agent;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RetriableException;

import java.net.SocketTimeoutException;
import java.util.Set;

public final class LlmFailures {

    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(408, 425, 429, 500, 502, 503, 504);
    private static final Set<String> RETRYABLE_MARKERS = Set.of(
            "(408)", "(429)", "(500)", "(502)", "(503)", "(504)",
            "UNAVAILABLE", "RESOURCE_EXHAUSTED", "DEADLINE_EXCEEDED",
            "high demand", "overloaded", "timed out", "timeout");

    private LlmFailures() {
    }

    public static boolean isRetryable(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof LlmNotConfiguredException || current instanceof NonRetriableException) {
                return false;
            }
            if (current instanceof RetriableException || current instanceof SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            if (current instanceof HttpException httpException
                    && RETRYABLE_STATUS_CODES.contains(httpException.statusCode())) {
                return true;
            }
            if (mentionsRetryableFailure(current.getMessage())) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static boolean mentionsRetryableFailure(String message) {
        if (message == null) {
            return false;
        }
        for (String marker : RETRYABLE_MARKERS) {
            if (message.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
