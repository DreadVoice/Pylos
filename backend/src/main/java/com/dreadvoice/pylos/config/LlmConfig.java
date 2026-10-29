package com.dreadvoice.pylos.config;

import com.dreadvoice.pylos.agent.LlmNotConfiguredException;
import com.dreadvoice.pylos.agent.ResilientChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public ChatModel chatModel(LlmProperties properties) {
        if (!StringUtils.hasText(properties.apiKey())) {
            log.warn("No LLM api key configured; copilot requests will be refused until GEMINI_API_KEY is set");
            return new UnconfiguredChatModel();
        }

        List<ResilientChatModel.NamedModel> models = new ArrayList<>();
        models.add(new ResilientChatModel.NamedModel(
                properties.modelName(), gemini(properties, properties.modelName())));

        if (StringUtils.hasText(properties.fallbackModelName())
                && !properties.fallbackModelName().equals(properties.modelName())) {
            models.add(new ResilientChatModel.NamedModel(
                    properties.fallbackModelName(), gemini(properties, properties.fallbackModelName())));
            log.info("Configured LLM model {} with fallback {}, {} attempt(s) each",
                    properties.modelName(), properties.fallbackModelName(), properties.maxAttempts());
        } else {
            log.info("Configured LLM model {} with {} attempt(s) and no fallback",
                    properties.modelName(), properties.maxAttempts());
        }

        return new ResilientChatModel(
                models,
                properties.maxAttempts(),
                properties.retryDelay(),
                ResilientChatModel.Sleeper.real());
    }

    private GoogleAiGeminiChatModel gemini(LlmProperties properties, String modelName) {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(properties.apiKey())
                .modelName(modelName)
                .temperature(properties.temperature())
                .maxOutputTokens(properties.maxOutputTokens())
                .timeout(properties.timeout())
                .logRequestsAndResponses(properties.logRequestsAndResponses())
                .build();
    }

    static class UnconfiguredChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            throw new LlmNotConfiguredException();
        }
    }
}
