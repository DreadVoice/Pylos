package com.dreadvoice.pylos.paypal;

import java.util.regex.Pattern;

public final class PayPalIdentifiers {

    public static final String INVOICE_ID_REGEX =
            "^INV2-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$";
    public static final String DISPUTE_ID_REGEX = "^PP-D-[0-9]{1,20}$";

    public static final Pattern INVOICE_ID = Pattern.compile(INVOICE_ID_REGEX);
    public static final Pattern DISPUTE_ID = Pattern.compile(DISPUTE_ID_REGEX);

    private PayPalIdentifiers() {
    }

    public static boolean isInvoiceId(String value) {
        return value != null && INVOICE_ID.matcher(value).matches();
    }

    public static boolean isDisputeId(String value) {
        return value != null && DISPUTE_ID.matcher(value).matches();
    }
}
