package com.dreadvoice.pylos.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "pylos.api.assist")
public record AssistRateLimitProperties(

        boolean enabled,

        @Min(1)
        int perClientLimit,

        Duration perClientWindow,

        @Min(1)
        int globalLimit,

        Duration globalWindow
) {

    public AssistRateLimitProperties {
        perClientWindow = perClientWindow == null ? Duration.ofMinutes(1) : perClientWindow;
        globalWindow = globalWindow == null ? Duration.ofDays(1) : globalWindow;
    }
}
