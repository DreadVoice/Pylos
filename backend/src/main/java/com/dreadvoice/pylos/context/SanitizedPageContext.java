package com.dreadvoice.pylos.context;

public record SanitizedPageContext(
        SiteType siteType,
        String url,
        String title,
        String text,
        PageHints hints,
        boolean truncated
) {
}
