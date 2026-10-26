package com.dreadvoice.pylos.action.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendReminderPayload(

        @NotBlank
        @Pattern(regexp = "^INV2-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")
        String invoiceId,

        @Size(max = 200)
        String subject,

        @Size(max = 2000)
        String note
) {
}
