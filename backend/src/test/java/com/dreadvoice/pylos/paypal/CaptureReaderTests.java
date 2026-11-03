package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.CaptureDetail;
import com.dreadvoice.pylos.paypal.dto.Money;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CaptureReaderTests {

    private static final String BASE_URL = "https://api-m.sandbox.paypal.test";
    private static final String CAPTURE_ID = "2S7263346H6677809";
    private static final String INVOICE_ID = "INV2-GS4Q-CBHK-S6QM-CC5V";

    private MockRestServiceServer server;
    private PayPalAuthClient authClient;
    private CaptureReader captureReader;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                });

        server = MockRestServiceServer.bindTo(builder).build();
        authClient = mock(PayPalAuthClient.class);
        when(authClient.accessToken()).thenReturn("token-one");
        captureReader = new CaptureReader(builder.build(), authClient);
    }

    @Test
    void readsTheCapturedAmount() {
        server.expect(once(), requestTo(BASE_URL + "/v2/payments/captures/" + CAPTURE_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-one"))
                .andRespond(withSuccess("{\"id\":\"" + CAPTURE_ID + "\",\"status\":\"COMPLETED\","
                                + "\"amount\":{\"currency_code\":\"USD\",\"value\":\"60.00\"},"
                                + "\"invoice_id\":\"" + INVOICE_ID + "\"}",
                        MediaType.APPLICATION_JSON));

        CaptureDetail capture = captureReader.get(CAPTURE_ID);

        assertThat(capture.id()).isEqualTo(CAPTURE_ID);
        assertThat(capture.status()).isEqualTo("COMPLETED");
        assertThat(capture.amount()).isEqualTo(new Money("USD", "60.00"));
        assertThat(capture.invoiceId()).isEqualTo(INVOICE_ID);
        server.verify();
    }

    @Test
    void rejectsAnythingThatIsNotShapedLikeACaptureId() {
        String[] rejected = {null, "", INVOICE_ID, "2s7263346h6677809", "2S7263346H667780",
                "2S7263346H66778099", "2S726334-H6677809"};

        for (String captureId : rejected) {
            assertThatThrownBy(() -> captureReader.get(captureId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not a PayPal capture id");
        }

        verifyNoInteractions(authClient);
        server.verify();
    }

    @Test
    void retriesOnceWithAFreshTokenAfterUnauthorized() {
        when(authClient.accessToken()).thenReturn("stale-token", "fresh-token");

        server.expect(once(), requestTo(BASE_URL + "/v2/payments/captures/" + CAPTURE_ID))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer stale-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(once(), requestTo(BASE_URL + "/v2/payments/captures/" + CAPTURE_ID))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fresh-token"))
                .andRespond(withSuccess("{\"id\":\"" + CAPTURE_ID + "\"}", MediaType.APPLICATION_JSON));

        assertThat(captureReader.get(CAPTURE_ID).id()).isEqualTo(CAPTURE_ID);

        verify(authClient).invalidate();
        verify(authClient, times(2)).accessToken();
        server.verify();
    }

    @Test
    void wrapsErrorResponsesInPayPalApiException() {
        server.expect(once(), requestTo(BASE_URL + "/v2/payments/captures/" + CAPTURE_ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"RESOURCE_NOT_FOUND\"}"));

        assertThatThrownBy(() -> captureReader.get(CAPTURE_ID))
                .isInstanceOf(PayPalApiException.class)
                .hasMessageContaining("404")
                .extracting(exception -> ((PayPalApiException) exception).getResponseBody())
                .asString()
                .contains("RESOURCE_NOT_FOUND");

        server.verify();
    }
}
