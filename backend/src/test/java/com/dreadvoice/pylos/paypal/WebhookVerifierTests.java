package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.config.PayPalProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WebhookVerifierTests {

    private static final String BASE_URL = "https://api-m.sandbox.paypal.test";
    private static final String VERIFY_URL = BASE_URL + "/v1/notifications/verify-webhook-signature";
    private static final String WEBHOOK_ID = "4JH86294D6974290U";
    private static final String BODY =
            "{\"id\":\"WH-1\",\"event_type\":\"INVOICING.INVOICE.PAID\",\"resource\":{\"x\":1}}";

    private MockRestServiceServer server;
    private RestClient.Builder builder;
    private PayPalAuthClient authClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        authClient = mock(PayPalAuthClient.class);
        when(authClient.accessToken()).thenReturn("token-one");
    }

    private WebhookVerifier verifierWith(String webhookId) {
        return new WebhookVerifier(builder.build(), authClient, objectMapper,
                new PayPalProperties("client", "secret", BASE_URL, webhookId));
    }

    private static WebhookHeaders headers() {
        return new WebhookHeaders(
                "SHA256withRSA",
                "https://api.sandbox.paypal.com/v1/notifications/certs/CERT-abc",
                "transmission-1",
                "signature-1",
                "2026-11-04T09:00:00Z");
    }

    @Test
    void acceptsAnEventPayPalConfirms() {
        server.expect(once(), requestTo(VERIFY_URL))
                .andExpect(jsonPath("$.webhook_id").value(WEBHOOK_ID))
                .andExpect(jsonPath("$.auth_algo").value("SHA256withRSA"))
                .andExpect(jsonPath("$.transmission_id").value("transmission-1"))
                .andExpect(jsonPath("$.transmission_sig").value("signature-1"))
                .andExpect(jsonPath("$.transmission_time").value("2026-11-04T09:00:00Z"))
                .andExpect(jsonPath("$.webhook_event.id").value("WH-1"))
                .andExpect(jsonPath("$.webhook_event.event_type").value("INVOICING.INVOICE.PAID"))
                .andRespond(withSuccess("{\"verification_status\":\"SUCCESS\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(verifierWith(WEBHOOK_ID).verify(headers(), BODY)).isTrue();
        server.verify();
    }

    @Test
    void rejectsAnEventPayPalDoesNotConfirm() {
        server.expect(once(), requestTo(VERIFY_URL))
                .andRespond(withSuccess("{\"verification_status\":\"FAILURE\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(verifierWith(WEBHOOK_ID).verify(headers(), BODY)).isFalse();
        server.verify();
    }

    @Test
    void sendsTheBodyToPayPalExactlyAsItArrived() {
        String spaced = "{ \"id\" : \"WH-2\" ,  \"event_type\":\"PAYMENT.CAPTURE.REFUNDED\" }";
        server.expect(once(), requestTo(VERIFY_URL))
                .andExpect(request -> assertThat(request.getBody().toString())
                        .contains("\"webhook_event\":" + spaced))
                .andRespond(withSuccess("{\"verification_status\":\"SUCCESS\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(verifierWith(WEBHOOK_ID).verify(headers(), spaced)).isTrue();
        server.verify();
    }

    @Test
    void failsClosedWhenNoWebhookIdIsConfigured() {
        assertThat(verifierWith(null).verify(headers(), BODY)).isFalse();
        assertThat(verifierWith("  ").verify(headers(), BODY)).isFalse();

        verifyNoInteractions(authClient);
        server.verify();
    }

    @Test
    void failsClosedWhenATransmissionHeaderIsMissing() {
        WebhookHeaders complete = headers();
        WebhookHeaders[] incomplete = {
                new WebhookHeaders(null, complete.certUrl(), complete.transmissionId(),
                        complete.transmissionSig(), complete.transmissionTime()),
                new WebhookHeaders(complete.authAlgo(), null, complete.transmissionId(),
                        complete.transmissionSig(), complete.transmissionTime()),
                new WebhookHeaders(complete.authAlgo(), complete.certUrl(), null,
                        complete.transmissionSig(), complete.transmissionTime()),
                new WebhookHeaders(complete.authAlgo(), complete.certUrl(), complete.transmissionId(),
                        null, complete.transmissionTime()),
                new WebhookHeaders(complete.authAlgo(), complete.certUrl(), complete.transmissionId(),
                        complete.transmissionSig(), null)
        };

        for (WebhookHeaders headers : incomplete) {
            assertThat(verifierWith(WEBHOOK_ID).verify(headers, BODY)).isFalse();
        }
        assertThat(verifierWith(WEBHOOK_ID).verify(null, BODY)).isFalse();

        verifyNoInteractions(authClient);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://api.sandbox.paypal.com/certs/CERT-abc",
            "https://paypal.com.evil.example.com/certs/CERT-abc",
            "https://evil.example.com/certs/CERT-abc",
            "https://notpaypal.com/certs/CERT-abc",
            "not a url at all"
    })
    void failsClosedWhenTheCertificateIsNotHostedByPayPal(String certUrl) {
        WebhookHeaders complete = headers();
        WebhookHeaders tampered = new WebhookHeaders(complete.authAlgo(), certUrl,
                complete.transmissionId(), complete.transmissionSig(), complete.transmissionTime());

        assertThat(verifierWith(WEBHOOK_ID).verify(tampered, BODY)).isFalse();

        verifyNoInteractions(authClient);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://paypal.com/certs/CERT-abc",
            "https://api.paypal.com/v1/notifications/certs/CERT-abc",
            "https://api.sandbox.paypal.com/v1/notifications/certs/CERT-abc"
    })
    void acceptsCertificatesHostedByPayPal(String certUrl) {
        server.expect(once(), requestTo(VERIFY_URL))
                .andRespond(withSuccess("{\"verification_status\":\"SUCCESS\"}",
                        MediaType.APPLICATION_JSON));

        WebhookHeaders complete = headers();
        WebhookHeaders withCert = new WebhookHeaders(complete.authAlgo(), certUrl,
                complete.transmissionId(), complete.transmissionSig(), complete.transmissionTime());

        assertThat(verifierWith(WEBHOOK_ID).verify(withCert, BODY)).isTrue();
        server.verify();
    }

    @Test
    void failsClosedOnAnEmptyBody() {
        assertThat(verifierWith(WEBHOOK_ID).verify(headers(), null)).isFalse();
        assertThat(verifierWith(WEBHOOK_ID).verify(headers(), "")).isFalse();

        verifyNoInteractions(authClient);
        server.verify();
    }

    @Test
    void failsClosedWhenPayPalCannotBeReached() {
        server.expect(once(), requestTo(VERIFY_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(verifierWith(WEBHOOK_ID).verify(headers(), BODY)).isFalse();
        server.verify();
    }

    @Test
    void failsClosedWhenPayPalAnswersWithoutAVerificationStatus() {
        server.expect(once(), requestTo(VERIFY_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(verifierWith(WEBHOOK_ID).verify(headers(), BODY)).isFalse();
        server.verify();
    }
}
