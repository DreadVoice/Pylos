import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { NotificationsView, NotificationView } from "../shared/notifications";
import { Notifications } from "./Notifications";

afterEach(cleanup);

const NOW = Date.parse("2026-11-04T12:00:00Z");

function notification(overrides: Partial<NotificationView> = {}): NotificationView {
  return {
    id: "n-1",
    event_type: "INVOICING.INVOICE.PAID",
    summary: "Invoice 0058 was paid",
    invoice_id: "INV2-GS4Q-CBHK-S6QM-CC5V",
    currency_code: "USD",
    amount: "60.00",
    occurred_at: "2026-11-04T11:55:00Z",
    read: false,
    ...overrides
  };
}

function view(overrides: Partial<NotificationsView> = {}): NotificationsView {
  return { notifications: [notification()], unread_count: 1, ...overrides };
}

describe("Notifications", () => {
  it("shows the summary, the amount and how long ago it happened", () => {
    render(<Notifications view={view()} now={NOW} busy={false} onMarkRead={vi.fn()} />);

    expect(screen.getByText("Invoice 0058 was paid")).toBeDefined();
    expect(screen.getByText("USD 60.00")).toBeDefined();
    expect(screen.getByText("5m ago")).toBeDefined();
    expect(screen.getByText("1 new")).toBeDefined();
  });

  it("renders nothing at all when there is no activity", () => {
    const { container } = render(
      <Notifications
        view={{ notifications: [], unread_count: 0 }}
        now={NOW}
        busy={false}
        onMarkRead={vi.fn()}
      />
    );

    expect(container.firstChild).toBeNull();
  });

  it("offers mark all read only while something is unread", () => {
    const { rerender } = render(
      <Notifications view={view()} now={NOW} busy={false} onMarkRead={vi.fn()} />
    );
    expect(screen.getByText("Mark all read")).toBeDefined();

    rerender(
      <Notifications
        view={view({ notifications: [notification({ read: true })], unread_count: 0 })}
        now={NOW}
        busy={false}
        onMarkRead={vi.fn()}
      />
    );
    expect(screen.queryByText("Mark all read")).toBeNull();
    expect(screen.queryByText("1 new")).toBeNull();
  });

  it("marks read on click and shows it is working", () => {
    const onMarkRead = vi.fn();
    const { rerender } = render(
      <Notifications view={view()} now={NOW} busy={false} onMarkRead={onMarkRead} />
    );

    fireEvent.click(screen.getByText("Mark all read"));
    expect(onMarkRead).toHaveBeenCalledTimes(1);

    rerender(<Notifications view={view()} now={NOW} busy={true} onMarkRead={onMarkRead} />);
    expect((screen.getByText("Marking…") as HTMLButtonElement).disabled).toBe(true);
  });

  it("dims what has already been seen", () => {
    const { container } = render(
      <Notifications
        view={view({
          notifications: [notification({ read: true }), notification({ id: "n-2" })],
          unread_count: 1
        })}
        now={NOW}
        busy={false}
        onMarkRead={vi.fn()}
      />
    );

    const rows = container.querySelectorAll("li");
    expect(rows[0]!.className).toBe("seen");
    expect(rows[1]!.className).toBe("");
  });

  it("leaves out an amount the backend did not send", () => {
    render(
      <Notifications
        view={view({
          notifications: [notification({ amount: undefined, currency_code: undefined })],
          unread_count: 1
        })}
        now={NOW}
        busy={false}
        onMarkRead={vi.fn()}
      />
    );

    expect(screen.queryByText(/USD/)).toBeNull();
    expect(screen.getByText("Invoice 0058 was paid")).toBeDefined();
  });

  it("shows at most eight entries however many arrive", () => {
    const many = Array.from({ length: 20 }, (_, index) =>
      notification({ id: `n-${index}`, summary: `Event ${index}` })
    );

    const { container } = render(
      <Notifications
        view={{ notifications: many, unread_count: 20 }}
        now={NOW}
        busy={false}
        onMarkRead={vi.fn()}
      />
    );

    expect(container.querySelectorAll("li")).toHaveLength(8);
    expect(screen.getByText("20 new")).toBeDefined();
  });
});
