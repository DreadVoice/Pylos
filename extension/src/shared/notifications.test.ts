import { describe, expect, it } from "vitest";
import {
  badgeText,
  describeAmount,
  emptyNotifications,
  readNotifications,
  timeAgo,
  type NotificationView
} from "./notifications";

function notification(overrides: Partial<NotificationView> = {}): NotificationView {
  return {
    id: "5f35822f-5dfd-493b-b382-3ca2fb63d8d1",
    event_type: "INVOICING.INVOICE.PAID",
    summary: "Invoice 0058 was paid",
    invoice_id: "INV2-GS4Q-CBHK-S6QM-CC5V",
    currency_code: "USD",
    amount: "60.00",
    occurred_at: "2026-11-04T09:00:00Z",
    read: false,
    ...overrides
  };
}

describe("readNotifications", () => {
  it("reads what the backend sends", () => {
    const view = readNotifications({ notifications: [notification()], unread_count: 1 });

    expect(view.notifications).toHaveLength(1);
    expect(view.notifications[0]!.summary).toBe("Invoice 0058 was paid");
    expect(view.unread_count).toBe(1);
  });

  it("returns an empty view for anything that is not a notifications payload", () => {
    for (const bad of [null, undefined, "", 7, [], "not json"]) {
      expect(readNotifications(bad)).toEqual(emptyNotifications());
    }
  });

  it("drops entries that are missing the fields the panel renders", () => {
    const view = readNotifications({
      notifications: [
        notification(),
        { id: "x", summary: "no event type" },
        { summary: "no id", event_type: "A", occurred_at: "2026-11-04T09:00:00Z", read: false },
        null,
        "a string"
      ],
      unread_count: 1
    });

    expect(view.notifications).toHaveLength(1);
  });

  it("counts the unread entries itself when the backend sends no count", () => {
    const view = readNotifications({
      notifications: [notification(), notification({ id: "b", read: true })]
    });

    expect(view.unread_count).toBe(1);
  });

  it("refuses a negative or fractional unread count", () => {
    expect(readNotifications({ notifications: [], unread_count: -4 }).unread_count).toBe(0);
    expect(readNotifications({ notifications: [], unread_count: 2.7 }).unread_count).toBe(2);
    expect(readNotifications({ notifications: [], unread_count: Number.NaN }).unread_count).toBe(0);
  });
});

describe("badgeText", () => {
  it("shows nothing when there is nothing unread", () => {
    expect(badgeText(0)).toBe("");
    expect(badgeText(-1)).toBe("");
    expect(badgeText(Number.NaN)).toBe("");
  });

  it("shows the count up to nine", () => {
    expect(badgeText(1)).toBe("1");
    expect(badgeText(9)).toBe("9");
  });

  it("caps anything larger so the badge stays readable", () => {
    expect(badgeText(10)).toBe("9+");
    expect(badgeText(250)).toBe("9+");
  });
});

describe("describeAmount", () => {
  it("joins the currency and the value", () => {
    expect(describeAmount(notification())).toBe("USD 60.00");
    expect(describeAmount(notification({ currency_code: "inr" }))).toBe("INR 60.00");
  });

  it("gives nothing when there is no amount to show", () => {
    expect(describeAmount(notification({ amount: undefined }))).toBeNull();
    expect(describeAmount(notification({ amount: "" }))).toBeNull();
  });

  it("shows a bare value when the currency is missing", () => {
    expect(describeAmount(notification({ currency_code: undefined }))).toBe("60.00");
  });
});

describe("timeAgo", () => {
  const now = Date.parse("2026-11-04T12:00:00Z");

  it("describes recent events in words", () => {
    expect(timeAgo("2026-11-04T11:59:30Z", now)).toBe("just now");
    expect(timeAgo("2026-11-04T11:55:00Z", now)).toBe("5m ago");
    expect(timeAgo("2026-11-04T09:00:00Z", now)).toBe("3h ago");
    expect(timeAgo("2026-11-01T12:00:00Z", now)).toBe("3d ago");
  });

  it("says nothing for a timestamp it cannot read", () => {
    expect(timeAgo("not a timestamp", now)).toBe("");
    expect(timeAgo("", now)).toBe("");
  });

  it("shows a date instead of a useless day count once something is old", () => {
    expect(timeAgo("2015-09-25T23:14:14Z", now)).toBe("2015-09-25");
    expect(timeAgo("2026-08-01T12:00:00Z", now)).toBe("2026-08-01");
  });

  it("still counts days up to a month", () => {
    expect(timeAgo("2026-10-20T12:00:00Z", now)).toBe("15d ago");
    expect(timeAgo("2026-10-06T12:00:00Z", now)).toBe("29d ago");
  });
});
