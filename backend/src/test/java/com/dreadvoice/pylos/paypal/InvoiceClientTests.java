package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.BillingInfo;
import com.dreadvoice.pylos.paypal.dto.InvoiceDetail;
import com.dreadvoice.pylos.paypal.dto.InvoiceDraftRequest;
import com.dreadvoice.pylos.paypal.dto.InvoiceItem;
import com.dreadvoice.pylos.paypal.dto.InvoiceList;
import com.dreadvoice.pylos.paypal.dto.InvoiceName;
import com.dreadvoice.pylos.paypal.dto.InvoiceNotification;
import com.dreadvoice.pylos.paypal.dto.InvoiceRecipient;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import com.dreadvoice.pylos.paypal.dto.Invoicer;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InvoiceClientTests {

    private static final String BASE_URL = "https://api-m.sandbox.paypal.test";

    private MockRestServiceServer server;
    private PayPalAuthClient authClient;
    private InvoiceClient invoiceClient;

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
        invoiceClient = new InvoiceClient(builder.build(), authClient);
    }

    @Test
    void createDraftPostsSnakeCaseBodyAndReturnsSummary() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-one"))
                .andExpect(header("Prefer", "return=representation"))
                .andExpect(headerDoesNotExist("PayPal-Request-Id"))
                .andExpect(jsonPath("$.detail.invoice_number").value("INV-0001"))
                .andExpect(jsonPath("$.detail.invoice_date").value("2026-10-25"))
                .andExpect(jsonPath("$.primary_recipients[0].billing_info.email_address")
                        .value("buyer@personal.example.com"))
                .andExpect(jsonPath("$.items[0].unit_amount.value").value("250.00"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"INV2-NEW\",\"status\":\"DRAFT\"}"));

        InvoiceSummary summary = invoiceClient.createDraft(draftRequest());

        assertThat(summary.id()).isEqualTo("INV2-NEW");
        assertThat(summary.status()).isEqualTo("DRAFT");
        server.verify();
    }

    @Test
    void createDraftSendsRequestIdWhenProvided() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices"))
                .andExpect(header("PayPal-Request-Id", "action-42"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"INV2-NEW\",\"status\":\"DRAFT\"}"));

        invoiceClient.createDraft(draftRequest(), "action-42");

        server.verify();
    }

    @Test
    void getFetchesInvoiceById() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ABC"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-one"))
                .andRespond(withSuccess("{\"id\":\"INV2-ABC\",\"status\":\"SENT\","
                        + "\"due_amount\":{\"currency_code\":\"USD\",\"value\":\"250.00\"}}",
                        MediaType.APPLICATION_JSON));

        InvoiceSummary summary = invoiceClient.get("INV2-ABC");

        assertThat(summary.status()).isEqualTo("SENT");
        assertThat(summary.dueAmount().value()).isEqualTo("250.00");
        server.verify();
    }

    @Test
    void listSendsPagingQueryParameters() {
        server.expect(once(), requestTo(BASE_URL
                        + "/v2/invoicing/invoices?page=2&page_size=20&total_required=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"total_items\":1,\"total_pages\":1,"
                        + "\"items\":[{\"id\":\"INV2-ONE\",\"status\":\"PAID\"}]}",
                        MediaType.APPLICATION_JSON));

        InvoiceList list = invoiceClient.list(2, 20);

        assertThat(list.items()).hasSize(1);
        assertThat(list.totalItems()).isEqualTo(1);
        server.verify();
    }

    @Test
    void sendPostsNotificationToSendEndpoint() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ABC/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.send_to_recipient").value(true))
                .andExpect(jsonPath("$.subject").value("Your invoice"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        invoiceClient.send("INV2-ABC", notification());

        server.verify();
    }

    @Test
    void remindPostsNotificationToRemindEndpoint() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ABC/remind"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.note").value("A gentle reminder"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        invoiceClient.remind("INV2-ABC", notification());

        server.verify();
    }

    @Test
    void remindSendsAnIdempotencyKeyWhenProvided() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ABC/remind"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "action-77"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        invoiceClient.remind("INV2-ABC", notification(), "action-77");

        server.verify();
    }

    @Test
    void remindOmitsTheIdempotencyKeyWhenAbsent() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ABC/remind"))
                .andExpect(headerDoesNotExist("PayPal-Request-Id"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        invoiceClient.remind("INV2-ABC", notification());

        server.verify();
    }

    @Test
    void cancelPostsNotificationToCancelEndpoint() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ABC/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        invoiceClient.cancel("INV2-ABC", notification());

        server.verify();
    }

    @Test
    void generateNextInvoiceNumberReturnsNumber() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/generate-next-invoice-number"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"invoice_number\":\"0007\"}", MediaType.APPLICATION_JSON));

        assertThat(invoiceClient.generateNextInvoiceNumber()).isEqualTo("0007");
        server.verify();
    }

    @Test
    void createAndSendCreatesSendsAndReturnsFinalInvoice() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "action-42"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"INV2-NEW\",\"status\":\"DRAFT\"}"));
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-NEW/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "action-42-send"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-NEW"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"INV2-NEW\",\"status\":\"SENT\"}",
                        MediaType.APPLICATION_JSON));

        InvoiceSummary sent = invoiceClient.createAndSend(draftRequest(), notification(), "action-42");

        assertThat(sent.status()).isEqualTo("SENT");
        server.verify();
    }

    @Test
    void createAndSendDeletesTheDraftWhenSendFails() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"INV2-ORPHAN\",\"status\":\"DRAFT\"}"));
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ORPHAN/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ORPHAN"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatThrownBy(() -> invoiceClient.createAndSend(draftRequest(), notification(), null))
                .isInstanceOf(PayPalApiException.class);

        server.verify();
    }

    @Test
    void createAndSendKeepsSendFailureWhenCleanupAlsoFails() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"INV2-ORPHAN\",\"status\":\"DRAFT\"}"));
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ORPHAN/send"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ORPHAN"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> invoiceClient.createAndSend(draftRequest(), notification(), null))
                .isInstanceOf(PayPalApiException.class)
                .hasMessageContaining("500")
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).hasSize(1));

        server.verify();
    }

    @Test
    void deleteRemovesDraftInvoice() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ABC"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-one"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        invoiceClient.delete("INV2-ABC");

        server.verify();
    }

    @Test
    void retriesOnceWithFreshTokenAfterUnauthorized() {
        when(authClient.accessToken()).thenReturn("stale-token", "fresh-token");

        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ABC"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer stale-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices/INV2-ABC"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fresh-token"))
                .andRespond(withSuccess("{\"id\":\"INV2-ABC\",\"status\":\"SENT\"}",
                        MediaType.APPLICATION_JSON));

        InvoiceSummary summary = invoiceClient.get("INV2-ABC");

        assertThat(summary.id()).isEqualTo("INV2-ABC");
        verify(authClient).invalidate();
        verify(authClient, times(2)).accessToken();
        server.verify();
    }

    @Test
    void wrapsErrorResponseInPayPalApiException() {
        server.expect(once(), requestTo(BASE_URL + "/v2/invoicing/invoices"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"VALIDATION_ERROR\"}"));

        assertThatThrownBy(() -> invoiceClient.createDraft(draftRequest()))
                .isInstanceOf(PayPalApiException.class)
                .hasMessageContaining("422")
                .extracting(exception -> ((PayPalApiException) exception).getResponseBody())
                .asString()
                .contains("VALIDATION_ERROR");

        server.verify();
    }

    private static InvoiceDraftRequest draftRequest() {
        return new InvoiceDraftRequest(
                new InvoiceDetail("INV-0001", "USD", LocalDate.of(2026, 10, 25), "Thanks for your order"),
                new Invoicer(new InvoiceName("Pylos", "Seller"), "seller@business.example.com"),
                List.of(new InvoiceRecipient(
                        new BillingInfo(new InvoiceName("Test", "Buyer"), "buyer@personal.example.com"))),
                List.of(new InvoiceItem("Logo design", "Final artwork", "1", new Money("USD", "250.00")))
        );
    }

    private static InvoiceNotification notification() {
        return new InvoiceNotification("Your invoice", "A gentle reminder", false, true);
    }
}
