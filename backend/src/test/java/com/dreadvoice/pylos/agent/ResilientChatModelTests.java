package com.dreadvoice.pylos.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientChatModelTests {

    private static final ChatRequest REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("invoice this"))
            .build();

    private final List<Duration> sleeps = new ArrayList<>();
    private final ResilientChatModel.Sleeper recordingSleeper = sleeps::add;

    @Test
    void returnsTheFirstSuccessWithoutRetrying() {
        ScriptedModel primary = new ScriptedModel(reply("done"));

        ResilientChatModel model = modelWith(3, primary);

        assertThat(model.chat(REQUEST).aiMessage().text()).isEqualTo("done");
        assertThat(primary.calls()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retriesATransientFailureAndThenSucceeds() {
        ScriptedModel primary = new ScriptedModel(
                new InternalServerException("boom"),
                new RateLimitException("slow down"),
                reply("done"));

        ResilientChatModel model = modelWith(3, primary);

        assertThat(model.chat(REQUEST).aiMessage().text()).isEqualTo("done");
        assertThat(primary.calls()).isEqualTo(3);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void backsOffExponentiallyBetweenAttempts() {
        ScriptedModel primary = new ScriptedModel(
                new InternalServerException("boom"),
                new InternalServerException("boom"),
                reply("done"));

        modelWith(3, primary).chat(REQUEST);

        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Test
    void retriesTheRawRuntimeFailureTheGeminiModuleActuallyThrows() {
        ScriptedModel primary = new ScriptedModel(
                new RuntimeException("HTTP error (503): {\"error\":{\"status\":\"UNAVAILABLE\"}}"),
                reply("done"));

        assertThat(modelWith(2, primary).chat(REQUEST).aiMessage().text()).isEqualTo("done");
        assertThat(primary.calls()).isEqualTo(2);
    }

    @Test
    void retriesRetryableHttpStatusCodes() {
        for (int status : new int[]{408, 429, 500, 502, 503, 504}) {
            ScriptedModel primary = new ScriptedModel(
                    new HttpException(status, "upstream said no"), reply("done"));

            assertThat(modelWith(2, primary).chat(REQUEST).aiMessage().text()).isEqualTo("done");
            assertThat(primary.calls()).isEqualTo(2);
        }
    }

    @Test
    void doesNotRetryANonRetriableFailure() {
        ScriptedModel primary = new ScriptedModel(
                new InvalidRequestException("bad tool schema"), reply("done"));

        assertThatThrownBy(() -> modelWith(3, primary).chat(REQUEST))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(primary.calls()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void doesNotRetryABadApiKey() {
        ScriptedModel primary = new ScriptedModel(
                new AuthenticationException("api key not valid"), reply("done"));

        assertThatThrownBy(() -> modelWith(3, primary).chat(REQUEST))
                .isInstanceOf(AuthenticationException.class);

        assertThat(primary.calls()).isEqualTo(1);
    }

    @Test
    void doesNotRetryAnUnconfiguredModel() {
        ScriptedModel primary = new ScriptedModel(new LlmNotConfiguredException(), reply("done"));

        assertThatThrownBy(() -> modelWith(3, primary).chat(REQUEST))
                .isInstanceOf(LlmNotConfiguredException.class);

        assertThat(primary.calls()).isEqualTo(1);
    }

    @Test
    void fallsBackToTheSecondModelWhenThePrimaryKeepsFailing() {
        ScriptedModel primary = new ScriptedModel(
                new InternalServerException("boom"), new InternalServerException("boom"));
        ScriptedModel fallback = new ScriptedModel(reply("from fallback"));

        ResilientChatModel model = new ResilientChatModel(
                List.of(new ResilientChatModel.NamedModel("primary", primary),
                        new ResilientChatModel.NamedModel("fallback", fallback)),
                2, Duration.ofSeconds(1), recordingSleeper);

        assertThat(model.chat(REQUEST).aiMessage().text()).isEqualTo("from fallback");
        assertThat(primary.calls()).isEqualTo(2);
        assertThat(fallback.calls()).isEqualTo(1);
    }

    @Test
    void doesNotTouchTheFallbackForANonRetriableFailure() {
        ScriptedModel primary = new ScriptedModel(new InvalidRequestException("bad request"));
        ScriptedModel fallback = new ScriptedModel(reply("from fallback"));

        ResilientChatModel model = new ResilientChatModel(
                List.of(new ResilientChatModel.NamedModel("primary", primary),
                        new ResilientChatModel.NamedModel("fallback", fallback)),
                3, Duration.ofSeconds(1), recordingSleeper);

        assertThatThrownBy(() -> model.chat(REQUEST)).isInstanceOf(InvalidRequestException.class);
        assertThat(fallback.calls()).isZero();
    }

    @Test
    void throwsTheLastFailureWhenEveryModelIsExhausted() {
        ScriptedModel primary = new ScriptedModel(new InternalServerException("primary down"));
        ScriptedModel fallback = new ScriptedModel(new RateLimitException("fallback throttled"));

        ResilientChatModel model = new ResilientChatModel(
                List.of(new ResilientChatModel.NamedModel("primary", primary),
                        new ResilientChatModel.NamedModel("fallback", fallback)),
                1, Duration.ofSeconds(1), recordingSleeper);

        assertThatThrownBy(() -> model.chat(REQUEST))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("fallback throttled");
    }

    @Test
    void capsTheBackoffDelay() {
        ScriptedModel primary = new ScriptedModel(
                new InternalServerException("boom"), new InternalServerException("boom"),
                new InternalServerException("boom"), new InternalServerException("boom"),
                new InternalServerException("boom"), new InternalServerException("boom"),
                reply("done"));

        modelWith(7, primary).chat(REQUEST);

        assertThat(sleeps).allSatisfy(delay ->
                assertThat(delay).isLessThanOrEqualTo(Duration.ofSeconds(20)));
        assertThat(sleeps.getLast()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void refusesToBeBuiltWithNoModels() {
        assertThatThrownBy(() -> new ResilientChatModel(
                List.of(), 3, Duration.ofSeconds(1), recordingSleeper))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ResilientChatModel modelWith(int maxAttempts, ChatModel primary) {
        sleeps.clear();
        return new ResilientChatModel(
                List.of(new ResilientChatModel.NamedModel("primary", primary)),
                maxAttempts, Duration.ofSeconds(1), recordingSleeper);
    }

    private static ChatResponse reply(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    private static final class ScriptedModel implements ChatModel {

        private final Deque<Object> script = new ArrayDeque<>();
        private int calls;

        private ScriptedModel(Object... scripted) {
            script.addAll(List.of(scripted));
        }

        private int calls() {
            return calls;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            calls++;
            Object next = script.poll();
            if (next instanceof RuntimeException failure) {
                throw failure;
            }
            if (next instanceof ChatResponse response) {
                return response;
            }
            throw new IllegalStateException("Script exhausted after " + calls + " calls");
        }
    }
}
