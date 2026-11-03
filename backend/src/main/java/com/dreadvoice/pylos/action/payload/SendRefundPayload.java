package com.dreadvoice.pylos.action.payload;

import com.dreadvoice.pylos.paypal.PayPalIdentifiers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendRefundPayload(

        @NotBlank
        @Pattern(regexp = PayPalIdentifiers.INVOICE_ID_REGEX)
        String invoiceId,

        @NotBlank
        @Size(min = 3, max = 3)
        String currencyCode,

        @NotBlank
        String amount,

        @Size(max = 2000)
        String noteToPayer
) {
}
