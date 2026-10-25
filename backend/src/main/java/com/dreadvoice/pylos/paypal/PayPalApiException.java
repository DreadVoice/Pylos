package com.dreadvoice.pylos.paypal;

import org.springframework.http.HttpStatusCode;

public class PayPalApiException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String responseBody;

    public PayPalApiException(String message, HttpStatusCode statusCode, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
