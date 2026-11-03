package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.Money;
import com.dreadvoice.pylos.paypal.dto.RefundDetail;
import com.dreadvoice.pylos.paypal.dto.RefundRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RefundClientTests {

    private static final String BASE_URL = "https://api-m.sandbox.paypal.test";
    private static final String CAPTURE_ID = "2S7263346H6677809";
    private static final String INVOICE_ID = "INV2-GS4Q-CBHK-S6QM-CC5V";

    private MockRestServiceServer server;
    private PayPalAuthClient authClient;
    private RefundClient refundClient;

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
        refundClient = new RefundClient(builder.build(), authClient);
    }

    @Test
    void refundPostsSnakeCaseBodyAndReturnsRepresentation() {
        server.expect(once(), requestTo(BASE_URL + "/v2/payments/captures/" + CAPTURE_ID + "/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Prefer", "return=representation"))
                .andExpect(header("PayPal-Request-Id", "action-42"))
                .andExpect(jsonPath("$.amount.currency_code").value("USD"))
                .andExpect(jsonPath("$.amount.value").value("15.00"))
                .andExpect(jsonPath("$.invoice_id").value(INVOICE_ID))
                .andExpect(jsonPath("$.note_to_payer").value("Refund for the damaged item"))
                .andRespond(withSuccess("{\"id\":\"1JU08902781691411\",\"status\":\"COMPLETED\","
                                + "\"amount\":{\"currency_code\":\"USD\",\"value\":\"15.00\"}}",
                        MediaType.APPLICATION_JSON));

        RefundDetail refund = refundClient.refund(
                CAPTURE_ID,
                new RefundRequest(new Money("USD", "15.00"), INVOICE_ID, "Refund for the damaged item"),
                "action-42");

        assertThat(refund.id()).isEqualTo("1JU08902781691411");
        assertThat(refund.status()).isEqualTo("COMPLETED");
        assertThat(refund.amount()).isEqualTo(new Money("USD", "15.00"));
        server.verify();
    }

    @Test
    void refundOmitsTheRequestIdHeaderWhenNoKeyIsGiven() {
        server.expect(once(), requestTo(BASE_URL + "/v2/payments/captures/" + CAPTURE_ID + "/refund"))
                .andExpect(headerDoesNotExist("PayPal-Request-Id"))
                .andRespond(withSuccess("{\"id\":\"1JU08902781691411\"}", MediaType.APPLICATION_JSON));

        refundClient.refund(CAPTURE_ID, new RefundRequest(new Money("USD", "1.00"), null, null), null);

        server.verify();
    }

    @Test
    void rejectsAnythingThatIsNotShapedLikeACaptureId() {
        String[] rejected = {null, "", INVOICE_ID, "2s7263346h6677809", "2S7263346H667780",
                "2S7263346H66778099", "2S726334-H6677809"};

        for (String captureId : rejected) {
            assertThatThrownBy(() -> refundClient.refund(
                    captureId, new RefundRequest(new Money("USD", "1.00"), null, null), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not a PayPal capture id");
        }

        verifyNoInteractions(authClient);
        server.verify();
    }

    @Test
    void wrapsRefundFailuresInPayPalApiException() {
        server.expect(once(), requestTo(BASE_URL + "/v2/payments/captures/" + CAPTURE_ID + "/refund"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"REFUND_AMOUNT_EXCEEDED\"}"));

        assertThatThrownBy(() -> refundClient.refund(
                CAPTURE_ID, new RefundRequest(new Money("USD", "9999.00"), null, null), "action-7"))
                .isInstanceOf(PayPalApiException.class)
                .hasMessageContaining("422")
                .extracting(exception -> ((PayPalApiException) exception).getResponseBody())
                .asString()
                .contains("REFUND_AMOUNT_EXCEEDED");

        server.verify();
    }
}
