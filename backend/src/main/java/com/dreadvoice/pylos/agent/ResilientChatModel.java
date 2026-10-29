package com.dreadvoice.pylos.agent;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

public class ResilientChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(ResilientChatModel.class);
    private static final Duration MAX_DELAY = Duration.ofSeconds(20);

    private final List<NamedModel> models;
    private final int maxAttemptsPerModel;
    private final Duration initialDelay;
    private final Sleeper sleeper;

    public ResilientChatModel(List<NamedModel> models,
                              int maxAttemptsPerModel,
                              Duration initialDelay,
                              Sleeper sleeper) {
        if (models.isEmpty()) {
            throw new IllegalArgumentException("At least one chat model is required");
        }
        this.models = List.copyOf(models);
        this.maxAttemptsPerModel = Math.max(1, maxAttemptsPerModel);
        this.initialDelay = initialDelay;
        this.sleeper = sleeper;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        RuntimeException lastFailure = null;

        for (NamedModel named : models) {
            for (int attempt = 1; attempt <= maxAttemptsPerModel; attempt++) {
                try {
                    return named.model().chat(chatRequest);
                } catch (RuntimeException failure) {
                    if (!LlmFailures.isRetryable(failure)) {
                        throw failure;
                    }
                    lastFailure = failure;
                    log.warn("Model {} attempt {} of {} failed transiently: {}",
                            named.name(), attempt, maxAttemptsPerModel, failure.getClass().getSimpleName());
                    if (attempt < maxAttemptsPerModel) {
                        sleeper.sleep(delayForAttempt(attempt));
                    }
                }
            }
            if (models.size() > 1) {
                log.warn("Model {} exhausted its attempts; trying the next configured model", named.name());
            }
        }

        throw lastFailure;
    }

    private Duration delayForAttempt(int attempt) {
        Duration delay = initialDelay.multipliedBy(1L << (attempt - 1));
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    public record NamedModel(String name, ChatModel model) {
    }

    public interface Sleeper {

        void sleep(Duration duration);

        static Sleeper real() {
            return duration -> {
                try {
                    Thread.sleep(duration.toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting to retry", interrupted);
                }
            };
        }
    }
}
