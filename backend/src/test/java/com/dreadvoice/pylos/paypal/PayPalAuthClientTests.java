package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.config.PayPalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PayPalAuthClientTests {

    private static final String BASE_URL = "https://api-m.sandbox.paypal.test";
    private static final Instant START = Instant.parse("2026-10-25T10:00:00Z");

    private MockRestServiceServer server;
    private MutableClock clock;
    private PayPalAuthClient authClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(START);
        PayPalProperties properties = new PayPalProperties("client-id", "client-secret", BASE_URL, "");
        authClient = new PayPalAuthClient(builder.build(), properties, clock);
    }

    @Test
    void requestsTokenWithBasicAuthAndClientCredentialsGrant() {
        String expectedBasicAuth = "Basic " + Base64.getEncoder()
                .encodeToString("client-id:client-secret".getBytes());

        server.expect(once(), requestTo(BASE_URL + "/v1/oauth2/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedBasicAuth))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(formData("grant_type", "client_credentials")))
                .andRespond(withSuccess(tokenJson("token-one", 32400), MediaType.APPLICATION_JSON));

        assertThat(authClient.accessToken()).isEqualTo("token-one");
        server.verify();
    }

    @Test
    void reusesCachedTokenWithinLifetime() {
        server.expect(once(), requestTo(BASE_URL + "/v1/oauth2/token"))
                .andRespond(withSuccess(tokenJson("token-one", 32400), MediaType.APPLICATION_JSON));

        assertThat(authClient.accessToken()).isEqualTo("token-one");
        clock.advance(Duration.ofHours(8));
        assertThat(authClient.accessToken()).isEqualTo("token-one");

        server.verify();
    }

    @Test
    void requestsNewTokenOnceExpiryMarginIsReached() {
        server.expect(once(), requestTo(BASE_URL + "/v1/oauth2/token"))
                .andRespond(withSuccess(tokenJson("token-one", 120), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/v1/oauth2/token"))
                .andRespond(withSuccess(tokenJson("token-two", 120), MediaType.APPLICATION_JSON));

        assertThat(authClient.accessToken()).isEqualTo("token-one");
        clock.advance(Duration.ofSeconds(61));
        assertThat(authClient.accessToken()).isEqualTo("token-two");

        server.verify();
    }

    @Test
    void requestsNewTokenAfterInvalidate() {
        server.expect(once(), requestTo(BASE_URL + "/v1/oauth2/token"))
                .andRespond(withSuccess(tokenJson("token-one", 32400), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/v1/oauth2/token"))
                .andRespond(withSuccess(tokenJson("token-two", 32400), MediaType.APPLICATION_JSON));

        assertThat(authClient.accessToken()).isEqualTo("token-one");
        authClient.invalidate();
        assertThat(authClient.accessToken()).isEqualTo("token-two");

        server.verify();
    }

    @Test
    void throwsWhenCredentialsAreRejected() {
        server.expect(once(), requestTo(BASE_URL + "/v1/oauth2/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_client\"}"));

        assertThatThrownBy(() -> authClient.accessToken())
                .isInstanceOf(PayPalAuthException.class)
                .hasMessageContaining("Failed to obtain a PayPal access token");

        server.verify();
    }

    @Test
    void throwsWhenResponseHasNoAccessToken() {
        server.expect(once(), requestTo(BASE_URL + "/v1/oauth2/token"))
                .andRespond(withSuccess("{\"token_type\":\"Bearer\",\"expires_in\":32400}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> authClient.accessToken())
                .isInstanceOf(PayPalAuthException.class)
                .hasMessageContaining("no access token");

        server.verify();
    }

    @Test
    void throwsWhenResponseHasNoLifetime() {
        server.expect(once(), requestTo(BASE_URL + "/v1/oauth2/token"))
                .andRespond(withSuccess("{\"access_token\":\"token-one\",\"token_type\":\"Bearer\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> authClient.accessToken())
                .isInstanceOf(PayPalAuthException.class)
                .hasMessageContaining("no access token lifetime");

        server.verify();
    }

    private static org.springframework.util.MultiValueMap<String, String> formData(String key, String value) {
        org.springframework.util.LinkedMultiValueMap<String, String> form =
                new org.springframework.util.LinkedMultiValueMap<>();
        form.add(key, value);
        return form;
    }

    private static String tokenJson(String token, long expiresIn) {
        return "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\",\"expires_in\":"
                + expiresIn + ",\"scope\":\"invoicing\",\"app_id\":\"APP-TEST\"}";
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
