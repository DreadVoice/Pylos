package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.config.PayPalProperties;
import com.dreadvoice.pylos.paypal.dto.AccessTokenResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PayPalAuthClient {

    private static final String TOKEN_PATH = "/v1/oauth2/token";
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final PayPalProperties properties;
    private final Clock clock;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public PayPalAuthClient(RestClient payPalRestClient, PayPalProperties properties, Clock clock) {
        this.restClient = payPalRestClient;
        this.properties = properties;
        this.clock = clock;
    }

    public String accessToken() {
        CachedToken current = cachedToken.get();
        if (current != null && current.isUsableAt(clock.instant())) {
            return current.value();
        }
        synchronized (this) {
            CachedToken existing = cachedToken.get();
            if (existing != null && existing.isUsableAt(clock.instant())) {
                return existing.value();
            }
            CachedToken fetched = fetchToken();
            cachedToken.set(fetched);
            return fetched.value();
        }
    }

    public void invalidate() {
        cachedToken.set(null);
    }

    private CachedToken fetchToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        AccessTokenResponse response;
        try {
            response = restClient.post()
                    .uri(TOKEN_PATH)
                    .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AccessTokenResponse.class);
        } catch (RestClientException exception) {
            throw new PayPalAuthException("Failed to obtain a PayPal access token", exception);
        }

        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new PayPalAuthException("PayPal returned no access token");
        }
        if (response.expiresIn() == null || response.expiresIn() <= 0) {
            throw new PayPalAuthException("PayPal returned no access token lifetime");
        }

        Instant expiresAt = clock.instant().plusSeconds(response.expiresIn());
        return new CachedToken(response.accessToken(), expiresAt);
    }

    private record CachedToken(String value, Instant expiresAt) {

        private boolean isUsableAt(Instant now) {
            return now.isBefore(expiresAt.minus(EXPIRY_MARGIN));
        }
    }
}
