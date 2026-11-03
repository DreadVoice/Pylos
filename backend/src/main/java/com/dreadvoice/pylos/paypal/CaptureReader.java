package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.CaptureDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CaptureReader {

    private static final String CAPTURES_PATH = "/v2/payments/captures";

    private final RestClient restClient;
    private final PayPalAuthClient authClient;

    public CaptureReader(RestClient payPalRestClient, PayPalAuthClient authClient) {
        this.restClient = payPalRestClient;
        this.authClient = authClient;
    }

    public CaptureDetail get(String captureId) {
        if (!PayPalIdentifiers.isCaptureId(captureId)) {
            throw new IllegalArgumentException("Not a PayPal capture id: " + captureId);
        }
        return PayPalRequests.withToken(authClient, "payments", token -> restClient.get()
                .uri(CAPTURES_PATH + "/{captureId}", captureId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(CaptureDetail.class));
    }
}
