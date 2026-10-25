package com.dreadvoice.pylos.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "paypal")
public record PayPalProperties(

        @NotBlank
        String clientId,

        @NotBlank
        String clientSecret,

        @NotBlank
        String baseUrl,

        String webhookId
) {
}
