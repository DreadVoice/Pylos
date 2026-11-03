import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ActionView } from "../shared/actions";
import { ConfirmCard } from "./ConfirmCard";

afterEach(cleanup);

function refundAction(overrides: Partial<ActionView> = {}): ActionView {
  return {
    id: "99999999-8888-7777-6666-555555555555",
    action_type: "REFUND",
    status: "PROPOSED",
    summary: "Refund USD 30.00 on invoice INV2-GS4Q-CBHK-S6QM-CC5V",
    payload: {
      invoice_id: "INV2-GS4Q-CBHK-S6QM-CC5V",
      currency_code: "USD",
      amount: "30.00",
      note_to_payer: "Sorry about the torn seam"
    },
    result_reference: null,
    error_message: null,
    created_at: "2026-11-03T09:40:00Z",
    updated_at: "2026-11-03T09:40:00Z",
    ...overrides
  };
}

describe("ConfirmCard for a refund", () => {
  it("shows the invoice, the amount and what it adds up to", () => {
    render(<ConfirmCard action={refundAction()} onApprove={vi.fn()} onReject={vi.fn()} />);

    expect(screen.getByText(/Refund USD 30.00/)).toBeDefined();
    expect((screen.getByLabelText("Invoice") as HTMLInputElement).value)
      .toBe("INV2-GS4Q-CBHK-S6QM-CC5V");
    expect((screen.getByLabelText("Refund amount") as HTMLInputElement).value).toBe("30.00");
    expect(screen.getByText("USD 30.00")).toBeDefined();
  });

  it("says it is approving a refund, not sending something", () => {
    render(<ConfirmCard action={refundAction()} onApprove={vi.fn()} onReject={vi.fn()} />);

    expect(screen.getByText("Approve and refund")).toBeDefined();
  });

  it("approves with the edited amount, not the proposed one", async () => {
    const onApprove = vi.fn(async (_payload: unknown) => ({
      ok: true,
      action: { ...refundAction(), status: "EXECUTED" as const, result_reference: "1JU089027816914" }
    }));
    render(<ConfirmCard action={refundAction()} onApprove={onApprove} onReject={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Refund amount"), { target: { value: "15.00" } });
    fireEvent.click(screen.getByText("Approve and refund"));

    await waitFor(() => expect(onApprove).toHaveBeenCalledTimes(1));
    expect(onApprove.mock.calls[0]![0]).toEqual({
      invoice_id: "INV2-GS4Q-CBHK-S6QM-CC5V",
      currency_code: "USD",
      amount: "15.00",
      note_to_payer: "Sorry about the torn seam"
    });
  });

  it("reports a refund that was refused for exceeding what is refundable", async () => {
    const onApprove = vi.fn(async (_payload: unknown) => ({
      ok: false,
      error: "That proposal failed validation.",
      violations: ["amount: must not exceed the 59.96 USD still refundable on INV2-GS4Q-CBHK-S6QM-CC5V"]
    }));
    render(<ConfirmCard action={refundAction()} onApprove={onApprove} onReject={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Refund amount"), { target: { value: "50000.00" } });
    fireEvent.click(screen.getByText("Approve and refund"));

    await waitFor(() => expect(screen.getByText(/still refundable/)).toBeDefined());
    expect(screen.getByText("Approve and refund")).toBeDefined();
  });

  it("warns instead of totalling when the amount is not a plain decimal", () => {
    render(<ConfirmCard action={refundAction()} onApprove={vi.fn()} onReject={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Refund amount"), { target: { value: "fifty thousand" } });

    expect(screen.getByText(/must be a plain decimal/)).toBeDefined();
  });

  it("reports a completed refund as refunded rather than sent", () => {
    render(
      <ConfirmCard
        action={refundAction({ status: "EXECUTED", result_reference: "1JU089027816914" })}
        onApprove={vi.fn()}
        onReject={vi.fn()}
      />
    );

    expect(screen.getByText(/Refunded. PayPal reference 1JU089027816914/)).toBeDefined();
    expect(screen.queryByLabelText("Refund amount")).toBeNull();
  });

  it("rejects without calling approve", async () => {
    const onApprove = vi.fn();
    const onReject = vi.fn(async () => ({
      ok: true,
      action: { ...refundAction(), status: "REJECTED" as const }
    }));
    render(<ConfirmCard action={refundAction()} onApprove={onApprove} onReject={onReject} />);

    fireEvent.click(screen.getByText("Reject"));

    await waitFor(() => expect(screen.getByText(/Nothing was sent/)).toBeDefined());
    expect(onApprove).not.toHaveBeenCalled();
  });
});
