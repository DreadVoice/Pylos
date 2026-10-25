package com.dreadvoice.pylos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PayPalPropertiesTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsAllProperties() {
        runner.withPropertyValues(
                "paypal.client-id=test-client-id",
                "paypal.client-secret=test-client-secret",
                "paypal.base-url=https://api-m.sandbox.paypal.com",
                "paypal.webhook-id=test-webhook-id"
        ).run(context -> {
            PayPalProperties properties = context.getBean(PayPalProperties.class);
            assertThat(properties.clientId()).isEqualTo("test-client-id");
            assertThat(properties.clientSecret()).isEqualTo("test-client-secret");
            assertThat(properties.baseUrl()).isEqualTo("https://api-m.sandbox.paypal.com");
            assertThat(properties.webhookId()).isEqualTo("test-webhook-id");
        });
    }

    @Test
    void allowsBlankWebhookId() {
        runner.withPropertyValues(
                "paypal.client-id=test-client-id",
                "paypal.client-secret=test-client-secret",
                "paypal.base-url=https://api-m.sandbox.paypal.com",
                "paypal.webhook-id="
        ).run(context -> assertThat(context).hasSingleBean(PayPalProperties.class));
    }

    @Test
    void rejectsBlankClientId() {
        runner.withPropertyValues(
                "paypal.client-id=",
                "paypal.client-secret=test-client-secret",
                "paypal.base-url=https://api-m.sandbox.paypal.com"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingBaseUrl() {
        runner.withPropertyValues(
                "paypal.client-id=test-client-id",
                "paypal.client-secret=test-client-secret"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(PayPalProperties.class)
    static class TestConfiguration {
    }
}
