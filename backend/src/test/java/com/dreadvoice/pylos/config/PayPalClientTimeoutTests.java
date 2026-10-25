package com.dreadvoice.pylos.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayPalClientTimeoutTests {

    private ServerSocket silentServer;
    private Thread acceptThread;

    @BeforeEach
    void startSilentServer() throws IOException {
        silentServer = new ServerSocket(0);
        acceptThread = new Thread(() -> {
            while (!silentServer.isClosed()) {
                try (Socket ignored = silentServer.accept()) {
                    Thread.sleep(Duration.ofSeconds(30));
                } catch (IOException | InterruptedException exception) {
                    return;
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @AfterEach
    void stopSilentServer() throws IOException {
        silentServer.close();
        acceptThread.interrupt();
    }

    @Test
    void readTimeoutAbortsRequestsThatNeverGetAResponse() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        HttpClientAutoConfiguration.class,
                        RestClientAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class, PayPalClientConfig.class)
                .withPropertyValues(
                        "spring.http.client.connect-timeout=5s",
                        "spring.http.client.read-timeout=2s",
                        "paypal.client-id=test-client-id",
                        "paypal.client-secret=test-client-secret",
                        "paypal.base-url=http://localhost:" + silentServer.getLocalPort())
                .run(context -> {
                    RestClient restClient = context.getBean(RestClient.class);
                    Instant startedAt = Instant.now();

                    assertThatThrownBy(() -> restClient.get()
                            .uri("/v2/invoicing/invoices")
                            .retrieve()
                            .body(String.class))
                            .isInstanceOf(ResourceAccessException.class);

                    assertThat(Duration.between(startedAt, Instant.now()))
                            .isLessThan(Duration.ofSeconds(10));
                });
    }

    @Configuration
    @EnableConfigurationProperties(PayPalProperties.class)
    static class TestConfiguration {
    }
}
