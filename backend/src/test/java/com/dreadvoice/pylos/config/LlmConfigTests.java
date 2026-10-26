package com.dreadvoice.pylos.config;

import com.dreadvoice.pylos.agent.LlmNotConfiguredException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmConfigTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class, LlmConfig.class);

    @Test
    void buildsAGeminiModelWhenAnApiKeyIsConfigured() {
        runner.withPropertyValues(
                        "pylos.llm.api-key=test-api-key",
                        "pylos.llm.model-name=gemini-3.8-flash")
                .run(context -> assertThat(context.getBean(ChatModel.class))
                        .isInstanceOf(GoogleAiGeminiChatModel.class));
    }

    @Test
    void fallsBackToAModelThatRefusesClearlyWhenNoKeyIsSet() {
        runner.withPropertyValues(
                        "pylos.llm.api-key=",
                        "pylos.llm.model-name=gemini-3.8-flash")
                .run(context -> {
                    ChatModel chatModel = context.getBean(ChatModel.class);

                    assertThat(chatModel).isNotInstanceOf(GoogleAiGeminiChatModel.class);
                    assertThatThrownBy(() -> chatModel.chat("hello"))
                            .isInstanceOf(LlmNotConfiguredException.class)
                            .hasMessageContaining("GEMINI_API_KEY");
                });
    }

    @Test
    void startsWithoutAnyApiKeyProperty() {
        runner.withPropertyValues("pylos.llm.model-name=gemini-3.8-flash")
                .run(context -> assertThat(context).hasSingleBean(ChatModel.class));
    }

    @Test
    void requiresAModelName() {
        runner.withPropertyValues("pylos.llm.api-key=test-api-key")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void appliesDefaultsForOptionalTuningProperties() {
        runner.withPropertyValues(
                        "pylos.llm.api-key=test-api-key",
                        "pylos.llm.model-name=gemini-3.8-flash")
                .run(context -> {
                    LlmProperties properties = context.getBean(LlmProperties.class);

                    assertThat(properties.temperature()).isEqualTo(0.2);
                    assertThat(properties.maxOutputTokens()).isEqualTo(2048);
                    assertThat(properties.timeout()).hasSeconds(30);
                    assertThat(properties.logRequestsAndResponses()).isFalse();
                });
    }

    @Configuration
    @EnableConfigurationProperties(LlmProperties.class)
    static class TestConfiguration {
    }
}
