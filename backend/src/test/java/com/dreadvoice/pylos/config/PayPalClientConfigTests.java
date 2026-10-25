package com.dreadvoice.pylos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class PayPalClientConfigTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class, PayPalClientConfig.class)
            .withPropertyValues(
                    "paypal.client-id=test-client-id",
                    "paypal.client-secret=test-client-secret",
                    "paypal.base-url=https://api-m.sandbox.paypal.com"
            );

    @Test
    void createsPayPalRestClient() {
        runner.run(context -> assertThat(context).hasSingleBean(RestClient.class));
    }

    @Configuration
    @EnableConfigurationProperties(PayPalProperties.class)
    static class TestConfiguration {
    }
}
