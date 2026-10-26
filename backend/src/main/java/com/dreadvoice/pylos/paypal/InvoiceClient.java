package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.InvoiceDraftRequest;
import com.dreadvoice.pylos.paypal.dto.InvoiceList;
import com.dreadvoice.pylos.paypal.dto.InvoiceNotification;
import com.dreadvoice.pylos.paypal.dto.InvoiceNumber;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Function;

@Component
public class InvoiceClient {

    private static final String INVOICES_PATH = "/v2/invoicing/invoices";
    private static final String NEXT_INVOICE_NUMBER_PATH = "/v2/invoicing/generate-next-invoice-number";
    private static final String REQUEST_ID_HEADER = "PayPal-Request-Id";
    private static final String PREFER_HEADER = "Prefer";
    private static final String RETURN_REPRESENTATION = "return=representation";

    private final RestClient restClient;
    private final PayPalAuthClient authClient;

    public InvoiceClient(RestClient payPalRestClient, PayPalAuthClient authClient) {
        this.restClient = payPalRestClient;
        this.authClient = authClient;
    }

    public InvoiceSummary createDraft(InvoiceDraftRequest request) {
        return createDraft(request, null);
    }

    public InvoiceSummary createDraft(InvoiceDraftRequest request, String requestId) {
        return withToken(token -> restClient.post()
                .uri(INVOICES_PATH)
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.set(PREFER_HEADER, RETURN_REPRESENTATION);
                    if (StringUtils.hasText(requestId)) {
                        headers.set(REQUEST_ID_HEADER, requestId);
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InvoiceSummary.class));
    }

    public InvoiceSummary get(String invoiceId) {
        return withToken(token -> restClient.get()
                .uri(INVOICES_PATH + "/{invoiceId}", invoiceId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(InvoiceSummary.class));
    }

    public InvoiceList list(int page, int pageSize) {
        return withToken(token -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path(INVOICES_PATH)
                        .queryParam("page", page)
                        .queryParam("page_size", pageSize)
                        .queryParam("total_required", true)
                        .build())
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(InvoiceList.class));
    }

    public void send(String invoiceId, InvoiceNotification notification) {
        send(invoiceId, notification, null);
    }

    public void send(String invoiceId, InvoiceNotification notification, String requestId) {
        withToken(token -> restClient.post()
                .uri(INVOICES_PATH + "/{invoiceId}/send", invoiceId)
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    if (StringUtils.hasText(requestId)) {
                        headers.set(REQUEST_ID_HEADER, requestId);
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(notification)
                .retrieve()
                .toBodilessEntity());
    }

    public InvoiceSummary createAndSend(InvoiceDraftRequest request,
                                        InvoiceNotification notification,
                                        String requestId) {
        InvoiceSummary draft = createDraft(request, requestId);
        try {
            send(draft.id(), notification, sendRequestId(requestId));
        } catch (RuntimeException sendFailure) {
            discardDraft(draft.id(), sendFailure);
            throw sendFailure;
        }
        return get(draft.id());
    }

    public void delete(String invoiceId) {
        withToken(token -> restClient.delete()
                .uri(INVOICES_PATH + "/{invoiceId}", invoiceId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity());
    }

    public void remind(String invoiceId, InvoiceNotification notification) {
        remind(invoiceId, notification, null);
    }

    public void remind(String invoiceId, InvoiceNotification notification, String requestId) {
        withToken(token -> restClient.post()
                .uri(INVOICES_PATH + "/{invoiceId}/remind", invoiceId)
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    if (StringUtils.hasText(requestId)) {
                        headers.set(REQUEST_ID_HEADER, requestId);
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(notification)
                .retrieve()
                .toBodilessEntity());
    }

    public void cancel(String invoiceId, InvoiceNotification notification) {
        withToken(token -> restClient.post()
                .uri(INVOICES_PATH + "/{invoiceId}/cancel", invoiceId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(notification)
                .retrieve()
                .toBodilessEntity());
    }

    public String generateNextInvoiceNumber() {
        InvoiceNumber invoiceNumber = withToken(token -> restClient.post()
                .uri(NEXT_INVOICE_NUMBER_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(InvoiceNumber.class));
        if (invoiceNumber == null || !StringUtils.hasText(invoiceNumber.invoiceNumber())) {
            throw new PayPalApiException("PayPal returned no invoice number",
                    HttpStatus.BAD_GATEWAY, null, null);
        }
        return invoiceNumber.invoiceNumber();
    }

    private void discardDraft(String invoiceId, RuntimeException sendFailure) {
        try {
            delete(invoiceId);
        } catch (RuntimeException deleteFailure) {
            sendFailure.addSuppressed(deleteFailure);
        }
    }

    private String sendRequestId(String requestId) {
        return StringUtils.hasText(requestId) ? requestId + "-send" : null;
    }

    private <T> T withToken(Function<String, T> call) {
        try {
            return call.apply(authClient.accessToken());
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            authClient.invalidate();
            try {
                return call.apply(authClient.accessToken());
            } catch (RestClientResponseException retryFailure) {
                throw apiException(retryFailure);
            }
        } catch (RestClientResponseException failure) {
            throw apiException(failure);
        }
    }

    private PayPalApiException apiException(RestClientResponseException exception) {
        return new PayPalApiException(
                "PayPal invoicing request failed with status " + exception.getStatusCode().value(),
                exception.getStatusCode(),
                exception.getResponseBodyAsString(),
                exception);
    }
}
