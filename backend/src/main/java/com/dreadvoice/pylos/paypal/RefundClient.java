package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.RefundDetail;
import com.dreadvoice.pylos.paypal.dto.RefundRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class RefundClient {

    private static final String CAPTURES_PATH = "/v2/payments/captures";
    private static final String REQUEST_ID_HEADER = "PayPal-Request-Id";
    private static final String PREFER_HEADER = "Prefer";
    private static final String RETURN_REPRESENTATION = "return=representation";

    private final RestClient restClient;
    private final PayPalAuthClient authClient;

    public RefundClient(RestClient payPalRestClient, PayPalAuthClient authClient) {
        this.restClient = payPalRestClient;
        this.authClient = authClient;
    }

    public RefundDetail refund(String captureId, RefundRequest request, String requestId) {
        if (!PayPalIdentifiers.isCaptureId(captureId)) {
            throw new IllegalArgumentException("Not a PayPal capture id: " + captureId);
        }
        return PayPalRequests.withToken(authClient, "payments", token -> restClient.post()
                .uri(CAPTURES_PATH + "/{captureId}/refund", captureId)
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
                .body(RefundDetail.class));
    }
}
