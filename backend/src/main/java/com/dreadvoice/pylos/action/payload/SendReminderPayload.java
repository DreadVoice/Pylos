package com.dreadvoice.pylos.action.payload;

import com.dreadvoice.pylos.paypal.PayPalIdentifiers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendReminderPayload(

        @NotBlank
        @Pattern(regexp = PayPalIdentifiers.INVOICE_ID_REGEX)
        String invoiceId,

        @Size(max = 200)
        String subject,

        @Size(max = 2000)
        String note
) {
}
