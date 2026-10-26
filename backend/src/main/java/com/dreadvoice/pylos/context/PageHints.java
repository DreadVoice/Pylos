package com.dreadvoice.pylos.context;

import java.util.List;

public record PageHints(
        List<String> invoiceIds,
        List<String> disputeIds
) {

    public PageHints {
        invoiceIds = invoiceIds == null ? List.of() : List.copyOf(invoiceIds);
        disputeIds = disputeIds == null ? List.of() : List.copyOf(disputeIds);
    }

    public static PageHints empty() {
        return new PageHints(List.of(), List.of());
    }
}
