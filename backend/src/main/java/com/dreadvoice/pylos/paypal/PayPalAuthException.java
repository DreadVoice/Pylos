package com.dreadvoice.pylos.paypal;

public class PayPalAuthException extends RuntimeException {

    public PayPalAuthException(String message) {
        super(message);
    }

    public PayPalAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
