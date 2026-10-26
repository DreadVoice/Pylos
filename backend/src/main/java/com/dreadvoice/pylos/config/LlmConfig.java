package com.dreadvoice.pylos.config;

import com.dreadvoice.pylos.agent.LlmNotConfiguredException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public ChatModel chatModel(LlmProperties properties) {
        if (!StringUtils.hasText(properties.apiKey())) {
            log.warn("No LLM api key configured; copilot requests will be refused until GEMINI_API_KEY is set");
            return new UnconfiguredChatModel();
        }
        log.info("Configuring LLM model {}", properties.modelName());
        return GoogleAiGeminiChatModel.builder()
                .apiKey(properties.apiKey())
                .modelName(properties.modelName())
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
