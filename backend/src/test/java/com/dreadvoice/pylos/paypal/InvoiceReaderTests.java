package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.InvoiceList;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InvoiceReaderTests {

    private static final String BASE_URL = "https://api-m.sandbox.paypal.test";
    private static final String INVOICE_ID = "INV2-GS4Q-CBHK-S6QM-CC5V";

    private MockRestServiceServer server;
    private PayPalAuthClient authClient;
    private InvoiceReader invoiceReader;

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
        invoiceReader = new InvoiceReader(builder.build(), authClient);
    }

    @Test
    void readsAnInvoiceIncludingItsPayments() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/" + INVOICE_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-one"))
                .andRespond(withSuccess("{\"id\":\"" + INVOICE_ID + "\",\"status\":\"PAID\","
                                + "\"payments\":{\"transactions\":[{\"payment_id\":\"2S7263346H6677809\"}]}}",
                        MediaType.APPLICATION_JSON));

        InvoiceSummary invoice = invoiceReader.get(INVOICE_ID);

        assertThat(invoice.status()).isEqualTo("PAID");
        assertThat(invoice.payments().transactions().getFirst().paymentId())
                .isEqualTo("2S7263346H6677809");
        server.verify();
    }

    @Test
    void listsInvoicesWithPagingQueryParameters() {
        server.expect(once(), requestTo(BASE_URL
                        + "/v2/invoicing/invoices?page=2&page_size=10&total_required=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"total_items\":58,\"total_pages\":6,\"items\":[]}",
                        MediaType.APPLICATION_JSON));

        InvoiceList invoices = invoiceReader.list(2, 10);

        assertThat(invoices.totalItems()).isEqualTo(58);
        assertThat(invoices.items()).isEmpty();
        server.verify();
    }

    @Test
    void refusesAnythingThatIsNotShapedLikeAnInvoiceId() {
        for (String rejected : new String[]{null, "", "2S7263346H6677809", "INV2-GS4Q"}) {
            assertThatThrownBy(() -> invoiceReader.get(rejected))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not a PayPal invoice id");
        }

        verifyNoInteractions(authClient);
        server.verify();
    }

    @Test
    void wrapsErrorResponsesInPayPalApiException() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/" + INVOICE_ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"RESOURCE_NOT_FOUND\"}"));

        assertThatThrownBy(() -> invoiceReader.get(INVOICE_ID))
                .isInstanceOf(PayPalApiException.class)
                .hasMessageContaining("404");

        server.verify();
    }
}
