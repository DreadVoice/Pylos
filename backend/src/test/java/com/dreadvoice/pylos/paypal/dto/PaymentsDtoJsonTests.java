package com.dreadvoice.pylos.paypal.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class PaymentsDtoJsonTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readsThePaymentsAndRefundsBlocksOfAPaidInvoice() throws Exception {
        String json = """
                {
                  "id": "INV2-GS4Q-CBHK-S6QM-CC5V",
                  "status": "PARTIALLY_REFUNDED",
                  "amount": { "currency_code": "USD", "value": "60.00" },
                  "payments": {
                    "paid_amount": { "currency_code": "USD", "value": "60.00" },
                    "transactions": [
                      {
                        "payment_id": "3C679366HH908993F",
                        "type": "PAYPAL",
                        "method": "PAYPAL",
                        "payment_date": "2026-11-01",
                        "amount": { "currency_code": "USD", "value": "60.00" }
                      }
                    ]
                  },
                  "refunds": {
                    "refund_amount": { "currency_code": "USD", "value": "15.00" },
                    "transactions": [
                      {
                        "recipient_transaction_id": "2W750046D1165843U",
                        "type": "PAYPAL",
                        "refund_date": "2026-11-02",
                        "amount": { "currency_code": "USD", "value": "15.00" }
                      }
                    ]
                  }
                }
                """;

        InvoiceSummary invoice = objectMapper.readValue(json, InvoiceSummary.class);

        InvoicePaymentTransaction payment = invoice.payments().transactions().getFirst();
        assertThat(payment.paymentId()).isEqualTo("3C679366HH908993F");
        assertThat(payment.method()).isEqualTo("PAYPAL");
        assertThat(payment.paymentDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(payment.amount()).isEqualTo(new Money("USD", "60.00"));
        assertThat(invoice.payments().paidAmount()).isEqualTo(new Money("USD", "60.00"));

        InvoiceRefundTransaction refund = invoice.refunds().transactions().getFirst();
        assertThat(refund.recipientTransactionId()).isEqualTo("2W750046D1165843U");
        assertThat(refund.refundDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(invoice.refunds().refundAmount()).isEqualTo(new Money("USD", "15.00"));
    }

    @Test
    void treatsMissingTransactionListsAsEmptyRatherThanNull() throws Exception {
        InvoicePayments payments = objectMapper.readValue(
                "{\"paid_amount\":{\"currency_code\":\"USD\",\"value\":\"60.00\"}}", InvoicePayments.class);
        InvoiceRefunds refunds = objectMapper.readValue("{}", InvoiceRefunds.class);

        assertThat(payments.transactions()).isEmpty();
        assertThat(refunds.transactions()).isEmpty();
        assertThat(refunds.refundAmount()).isNull();
    }

    @Test
    void serializesTheRefundRequestWithSnakeCaseFields() throws Exception {
        String json = objectMapper.writeValueAsString(new RefundRequest(
                new Money("USD", "15.00"), "INV2-GS4Q-CBHK-S6QM-CC5V", "Refund for the damaged item"));

        assertThat(json).contains("\"amount\":{\"currency_code\":\"USD\",\"value\":\"15.00\"}");
        assertThat(json).contains("\"invoice_id\":\"INV2-GS4Q-CBHK-S6QM-CC5V\"");
        assertThat(json).contains("\"note_to_payer\":\"Refund for the damaged item\"");
    }

    @Test
    void omitsNullFieldsFromTheRefundRequest() throws Exception {
        String json = objectMapper.writeValueAsString(
                new RefundRequest(new Money("USD", "15.00"), null, null));

        assertThat(json).doesNotContain("invoice_id");
        assertThat(json).doesNotContain("note_to_payer");
    }

    @Test
    void readsACaptureDetail() throws Exception {
        CaptureDetail capture = objectMapper.readValue("""
                {
                  "id": "3C679366HH908993F",
                  "status": "COMPLETED",
                  "amount": { "currency_code": "USD", "value": "60.00" },
                  "invoice_id": "INV2-GS4Q-CBHK-S6QM-CC5V",
                  "final_capture": true,
                  "seller_receivable_breakdown": {
                    "gross_amount": { "currency_code": "USD", "value": "60.00" }
                  }
                }
                """, CaptureDetail.class);

        assertThat(capture.id()).isEqualTo("3C679366HH908993F");
        assertThat(capture.status()).isEqualTo("COMPLETED");
        assertThat(capture.amount()).isEqualTo(new Money("USD", "60.00"));
        assertThat(capture.invoiceId()).isEqualTo("INV2-GS4Q-CBHK-S6QM-CC5V");
    }
}
