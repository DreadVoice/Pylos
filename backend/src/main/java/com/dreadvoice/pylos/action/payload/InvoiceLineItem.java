package com.dreadvoice.pylos.action.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvoiceLineItem(

        @NotBlank
        @Size(max = 200)
        String name,

        @Size(max = 1000)
        String description,

        @NotBlank
        String quantity,

        @NotBlank
        String unitAmount
) {
}
