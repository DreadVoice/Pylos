package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.config.PayPalProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Locale;

@Component
public class WebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookVerifier.class);

    private static final String VERIFY_PATH = "/v1/notifications/verify-webhook-signature";
    private static final String SUCCESS = "SUCCESS";
    private static final String PAYPAL_DOMAIN = "paypal.com";

    private final RestClient restClient;
    private final PayPalAuthClient authClient;
    private final ObjectMapper objectMapper;
    private final String webhookId;

    public WebhookVerifier(RestClient payPalRestClient,
                           PayPalAuthClient authClient,
                           ObjectMapper objectMapper,
                           PayPalProperties properties) {
        this.restClient = payPalRestClient;
        this.authClient = authClient;
        this.objectMapper = objectMapper;
        this.webhookId = properties.webhookId();
    }

    public boolean verify(WebhookHeaders headers, String rawBody) {
        if (!StringUtils.hasText(webhookId)) {
            log.warn("Rejecting a webhook because paypal.webhook-id is not configured");
            return false;
        }
        if (headers == null || !headers.complete()) {
            log.warn("Rejecting a webhook that is missing PayPal transmission headers");
            return false;
        }
        if (!isPayPalCertificate(headers.certUrl())) {
            log.warn("Rejecting a webhook whose certificate url is not a PayPal host");
            return false;
        }
        if (!StringUtils.hasText(rawBody)) {
            return false;
        }

        try {
            JsonNode verification = PayPalRequests.withToken(authClient, "webhook", token ->
                    restClient.post()
                            .uri(VERIFY_PATH)
                            .headers(http -> http.setBearerAuth(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(verificationRequest(headers, rawBody))
                            .retrieve()
                            .body(JsonNode.class));

            return verification != null
                    && SUCCESS.equals(verification.path("verification_status").asText(null));
        } catch (RuntimeException failure) {
            log.warn("Could not verify a webhook signature with PayPal: {}", failure.getMessage());
            return false;
        }
    }

    private String verificationRequest(WebhookHeaders headers, String rawBody) {
        return "{"
                + "\"auth_algo\":" + quote(headers.authAlgo()) + ","
                + "\"cert_url\":" + quote(headers.certUrl()) + ","
                + "\"transmission_id\":" + quote(headers.transmissionId()) + ","
                + "\"transmission_sig\":" + quote(headers.transmissionSig()) + ","
                + "\"transmission_time\":" + quote(headers.transmissionTime()) + ","
                + "\"webhook_id\":" + quote(webhookId) + ","
                + "\"webhook_event\":" + rawBody
                + "}";
    }

    private String quote(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private boolean isPayPalCertificate(String certUrl) {
        try {
            URI uri = URI.create(certUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return host.equals(PAYPAL_DOMAIN) || host.endsWith("." + PAYPAL_DOMAIN);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }
}
