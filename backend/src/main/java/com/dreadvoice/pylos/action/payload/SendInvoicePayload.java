package com.dreadvoice.pylos.action.payload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SendInvoicePayload(

        @NotBlank
        @Email
        @Pattern(regexp = ".+@.+[.].+")
        String recipientEmail,

        @Size(max = 140)
        String recipientGivenName,

        @Size(max = 140)
        String recipientSurname,

        @NotBlank
        @Size(min = 3, max = 3)
        String currencyCode,

        @Size(max = 2000)
        String note,

        @NotEmpty
        List<@Valid InvoiceLineItem> items
) {
}
