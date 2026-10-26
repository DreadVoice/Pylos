package com.dreadvoice.pylos.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "pylos.llm")
public record LlmProperties(

        String apiKey,

        @NotBlank
        String modelName,

        Double temperature,

        Integer maxOutputTokens,

        Duration timeout,

        boolean logRequestsAndResponses
) {

    public LlmProperties {
        temperature = temperature == null ? 0.2 : temperature;
        maxOutputTokens = maxOutputTokens == null ? 2048 : maxOutputTokens;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }
}
