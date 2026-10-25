package com.dreadvoice.pylos.paypal.dto;

import java.util.List;

public record InvoiceList(
        List<InvoiceSummary> items,
        Integer totalItems,
        Integer totalPages
) {
}
