package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.notification.Notification;
import com.dreadvoice.pylos.notification.WebhookEventHandler;
import com.dreadvoice.pylos.paypal.WebhookHeaders;
import com.dreadvoice.pylos.paypal.WebhookVerifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayPalWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class PayPalWebhookControllerTests {

    private static final String BODY =
            "{\"id\":\"WH-1\",\"event_type\":\"INVOICING.INVOICE.PAID\",\"resource\":{}}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookVerifier verifier;

    @MockitoBean
    private WebhookEventHandler handler;

    @Test
    void acceptsAnEventWhoseSignatureVerifies() throws Exception {
        when(verifier.verify(any(), anyString())).thenReturn(true);
        when(handler.handle(anyString())).thenReturn(Optional.of(new Notification(
                "WH-1", "INVOICING.INVOICE.PAID", "Invoice 0058 was paid",
                "INV2-A", "USD", "60.00", Instant.parse("2026-11-04T09:00:00Z"))));

        mockMvc.perform(signed(BODY)).andExpect(status().isNoContent());

        verify(handler).handle(BODY);
    }

    @Test
    void passesEveryTransmissionHeaderToTheVerifier() throws Exception {
        when(verifier.verify(any(), anyString())).thenReturn(true);
        when(handler.handle(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(signed(BODY)).andExpect(status().isNoContent());

        ArgumentCaptor<WebhookHeaders> captor = ArgumentCaptor.forClass(WebhookHeaders.class);
        verify(verifier).verify(captor.capture(), any());
        WebhookHeaders headers = captor.getValue();
        assertThat(headers.authAlgo()).isEqualTo("SHA256withRSA");
        assertThat(headers.certUrl()).isEqualTo("https://api.sandbox.paypal.com/certs/CERT-abc");
        assertThat(headers.transmissionId()).isEqualTo("transmission-1");
        assertThat(headers.transmissionSig()).isEqualTo("signature-1");
        assertThat(headers.transmissionTime()).isEqualTo("2026-11-04T09:00:00Z");
    }

    @Test
    void refusesAnEventWhoseSignatureDoesNotVerifyAndNeverRecordsIt() throws Exception {
        when(verifier.verify(any(), anyString())).thenReturn(false);

        mockMvc.perform(signed(BODY)).andExpect(status().isUnauthorized());

        verifyNoInteractions(handler);
    }

    @Test
    void refusesAnEventThatCarriesNoTransmissionHeadersAtAll() throws Exception {
        when(verifier.verify(any(), any())).thenReturn(false);

        mockMvc.perform(post("/webhooks/paypal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verify(handler, never()).handle(anyString());
    }

    @Test
    void stillAnswersWhenTheEventIsOneItIgnores() throws Exception {
        when(verifier.verify(any(), anyString())).thenReturn(true);
        when(handler.handle(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(signed("{\"id\":\"WH-9\",\"event_type\":\"CHECKOUT.ORDER.APPROVED\"}"))
                .andExpect(status().isNoContent());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signed(
            String body) {
        return post("/webhooks/paypal")
                .header(WebhookHeaders.AUTH_ALGO, "SHA256withRSA")
                .header(WebhookHeaders.CERT_URL, "https://api.sandbox.paypal.com/certs/CERT-abc")
                .header(WebhookHeaders.TRANSMISSION_ID, "transmission-1")
                .header(WebhookHeaders.TRANSMISSION_SIG, "signature-1")
                .header(WebhookHeaders.TRANSMISSION_TIME, "2026-11-04T09:00:00Z")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
