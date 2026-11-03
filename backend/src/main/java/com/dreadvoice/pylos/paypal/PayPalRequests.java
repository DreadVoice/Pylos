package com.dreadvoice.pylos.paypal;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Function;

final class PayPalRequests {

    private PayPalRequests() {
    }

    static <T> T withToken(PayPalAuthClient authClient, String api, Function<String, T> call) {
        try {
            return call.apply(authClient.accessToken());
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            authClient.invalidate();
            try {
                return call.apply(authClient.accessToken());
            } catch (RestClientResponseException retryFailure) {
                throw apiException(api, retryFailure);
            }
        } catch (RestClientResponseException failure) {
            throw apiException(api, failure);
        }
    }

    private static PayPalApiException apiException(String api, RestClientResponseException exception) {
        return new PayPalApiException(
                "PayPal " + api + " request failed with status " + exception.getStatusCode().value(),
                exception.getStatusCode(),
                exception.getResponseBodyAsString(),
                exception);
    }
}
