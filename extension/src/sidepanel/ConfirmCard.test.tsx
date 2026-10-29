import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ActionView } from "../shared/actions";
import { ConfirmCard } from "./ConfirmCard";

afterEach(cleanup);

function invoiceAction(overrides: Partial<ActionView> = {}): ActionView {
  return {
    id: "11111111-2222-3333-4444-555555555555",
    action_type: "SEND_INVOICE",
    status: "PROPOSED",
    summary: "Invoice USD 50.00 to buyer@personal.example.com (1 item)",
    payload: {
      recipient_email: "buyer@personal.example.com",
      currency_code: "USD",
      note: "Thanks",
      items: [{ name: "Hoodie", quantity: "2", unit_amount: "25.00" }]
    },
    result_reference: null,
    error_message: null,
    created_at: "2026-10-29T10:00:00Z",
    updated_at: "2026-10-29T10:00:00Z",
    ...overrides
  };
}

const approved = (action: ActionView): ActionView => ({
  ...action,
  status: "EXECUTED",
  result_reference: "INV2-NEW"
});

describe("ConfirmCard", () => {
  it("shows the summary and the editable amounts", () => {
    render(<ConfirmCard action={invoiceAction()} onApprove={vi.fn()} onReject={vi.fn()} />);

    expect(screen.getByText(/Invoice USD 50.00/)).toBeDefined();
    expect((screen.getByLabelText("Item 1 unit amount") as HTMLInputElement).value).toBe("25.00");
    expect(screen.getByText("USD 50.00")).toBeDefined();
  });

  it("approves with the payload exactly as proposed when nothing is edited", async () => {
    const onApprove = vi.fn(async (_payload: unknown) => ({ ok: true, action: approved(invoiceAction()) }));
    render(<ConfirmCard action={invoiceAction()} onApprove={onApprove} onReject={vi.fn()} />);

    fireEvent.click(screen.getByText("Approve and send"));

    await waitFor(() => expect(onApprove).toHaveBeenCalledTimes(1));
    expect(onApprove.mock.calls[0]![0]).toEqual(invoiceAction().payload);
  });

  it("approves with the edited amount, not the proposed one", async () => {
    const onApprove = vi.fn(async (_payload: unknown) => ({ ok: true, action: approved(invoiceAction()) }));
    render(<ConfirmCard action={invoiceAction()} onApprove={onApprove} onReject={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Item 1 unit amount"), { target: { value: "30.00" } });
    fireEvent.change(screen.getByLabelText("Item 1 quantity"), { target: { value: "3" } });
    fireEvent.click(screen.getByText("Approve and send"));

    await waitFor(() => expect(onApprove).toHaveBeenCalledTimes(1));
    const sent = onApprove.mock.calls[0]![0] as { items: { quantity: string; unit_amount: string }[] };
    expect(sent.items[0].unit_amount).toBe("30.00");
    expect(sent.items[0].quantity).toBe("3");
  });

  it("recomputes the total as amounts are edited", () => {
    render(<ConfirmCard action={invoiceAction()} onApprove={vi.fn()} onReject={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Item 1 unit amount"), { target: { value: "0.10" } });
    fireEvent.change(screen.getByLabelText("Item 1 quantity"), { target: { value: "3" } });

    expect(screen.getByText("USD 0.30")).toBeDefined();
  });

  it("refuses to show a total it cannot compute exactly", () => {
    render(<ConfirmCard action={invoiceAction()} onApprove={vi.fn()} onReject={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Item 1 unit amount"), { target: { value: "1e3" } });

    expect(screen.getByText(/Total unavailable/)).toBeDefined();
  });

  it("adds and removes line items", async () => {
    const onApprove = vi.fn(async (_payload: unknown) => ({ ok: true, action: approved(invoiceAction()) }));
    render(<ConfirmCard action={invoiceAction()} onApprove={onApprove} onReject={vi.fn()} />);

    fireEvent.click(screen.getByText("Add item"));
    expect(screen.getByLabelText("Item 2 name")).toBeDefined();

    fireEvent.click(screen.getByLabelText("Remove item 1"));
    fireEvent.click(screen.getByText("Approve and send"));

    await waitFor(() => expect(onApprove).toHaveBeenCalledTimes(1));
    const sent = onApprove.mock.calls[0]![0] as { items: unknown[] };
    expect(sent.items).toHaveLength(1);
  });

  it("reports the result once approved and stops offering the buttons", async () => {
    const onApprove = vi.fn(async (_payload: unknown) => ({ ok: true, action: approved(invoiceAction()) }));
    render(<ConfirmCard action={invoiceAction()} onApprove={onApprove} onReject={vi.fn()} />);

    fireEvent.click(screen.getByText("Approve and send"));

    await waitFor(() => expect(screen.getByText(/INV2-NEW/)).toBeDefined());
    expect(screen.getByText("EXECUTED")).toBeDefined();
    expect(screen.queryByText("Approve and send")).toBeNull();
  });

  it("shows backend validation violations against the card", async () => {
    const onApprove = vi.fn(async (_payload: unknown) => ({
      ok: false,
      error: "Action payload failed validation",
      violations: ["items[0].unitAmount: must not be negative"]
    }));
    render(<ConfirmCard action={invoiceAction()} onApprove={onApprove} onReject={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Item 1 unit amount"), { target: { value: "-5.00" } });
    fireEvent.click(screen.getByText("Approve and send"));

    await waitFor(() =>
      expect(screen.getByText("items[0].unitAmount: must not be negative")).toBeDefined()
    );
    expect(screen.getByText("Approve and send")).toBeDefined();
  });

  it("rejects without sending a payload", async () => {
    const onReject = vi.fn(async () => ({
      ok: true,
      action: { ...invoiceAction(), status: "REJECTED" as const }
    }));
    render(<ConfirmCard action={invoiceAction()} onApprove={vi.fn()} onReject={onReject} />);

    fireEvent.click(screen.getByText("Reject"));

    await waitFor(() => expect(screen.getByText(/Nothing was sent/)).toBeDefined());
    expect(onReject).toHaveBeenCalledWith();
  });

  it("shows a failed action's error instead of an editor", () => {
    render(
      <ConfirmCard
        action={invoiceAction({ status: "FAILED", error_message: "PayPal said 422" })}
        onApprove={vi.fn()}
        onReject={vi.fn()}
      />
    );

    expect(screen.getByText("PayPal said 422")).toBeDefined();
    expect(screen.queryByLabelText("Item 1 unit amount")).toBeNull();
  });

  it("edits a reminder instead of an invoice", async () => {
    const reminder = invoiceAction({
      action_type: "SEND_REMINDER",
      summary: "Reminder for invoice INV2-7YQ5-K2GD-GDGY-962L",
      payload: { invoice_id: "INV2-7YQ5-K2GD-GDGY-962L", subject: "Still unpaid" }
    });
    const onApprove = vi.fn(async (_payload: unknown) => ({ ok: true, action: approved(reminder) }));
    render(<ConfirmCard action={reminder} onApprove={onApprove} onReject={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Subject"), { target: { value: "Gentle nudge" } });
    fireEvent.click(screen.getByText("Approve and send"));

    await waitFor(() => expect(onApprove).toHaveBeenCalledTimes(1));
    expect(onApprove.mock.calls[0]![0]).toEqual({
      invoice_id: "INV2-7YQ5-K2GD-GDGY-962L",
      subject: "Gentle nudge"
    });
  });
});
