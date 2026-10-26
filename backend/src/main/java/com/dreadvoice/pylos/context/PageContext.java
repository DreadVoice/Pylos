package com.dreadvoice.pylos.context;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PageContext(

        @NotNull
        SiteType siteType,

        @Size(max = 2000)
        String url,

        @Size(max = 500)
        String title,

        @Size(max = 100000)
        String text,

        PageHints hints
) {

    public PageContext {
        hints = hints == null ? PageHints.empty() : hints;
    }
}
