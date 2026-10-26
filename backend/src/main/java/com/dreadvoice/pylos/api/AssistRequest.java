package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.context.PageContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssistRequest(

        @NotNull
        @Valid
        PageContext context,

        @Size(max = 2000)
        String instruction
) {
}
