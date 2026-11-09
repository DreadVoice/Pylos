export type NotificationView = {
  id: string;
  event_type: string;
  summary: string;
  invoice_id?: string;
  currency_code?: string;
  amount?: string;
  occurred_at: string;
  read: boolean;
};

export type NotificationsView = {
  notifications: NotificationView[];
  unread_count: number;
};

const MAX_BADGE = 9;
const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const MONTH = 30 * DAY;

export function emptyNotifications(): NotificationsView {
  return { notifications: [], unread_count: 0 };
}

export function readNotifications(data: unknown): NotificationsView {
  if (typeof data !== "object" || data === null) {
    return emptyNotifications();
  }

  const raw = data as { notifications?: unknown; unread_count?: unknown };
  const notifications = Array.isArray(raw.notifications)
    ? raw.notifications.filter(isNotification)
    : [];
  const unread = typeof raw.unread_count === "number" && Number.isFinite(raw.unread_count)
    ? Math.max(0, Math.trunc(raw.unread_count))
    : notifications.filter((notification) => !notification.read).length;

  return { notifications, unread_count: unread };
}

export function badgeText(unreadCount: number): string {
  if (!Number.isFinite(unreadCount) || unreadCount <= 0) {
    return "";
  }
  const whole = Math.trunc(unreadCount);
  return whole > MAX_BADGE ? `${MAX_BADGE}+` : String(whole);
}

export function describeAmount(notification: NotificationView): string | null {
  if (notification.amount === undefined || notification.amount === "") {
    return null;
  }
  const currency = (notification.currency_code ?? "").toUpperCase();
  return currency === "" ? notification.amount : `${currency} ${notification.amount}`;
}

export function timeAgo(occurredAt: string, now: number): string {
  const at = Date.parse(occurredAt);
  if (Number.isNaN(at)) {
    return "";
  }

  const elapsed = now - at;
  if (elapsed < MINUTE) {
    return "just now";
  }
  if (elapsed < HOUR) {
    return `${Math.floor(elapsed / MINUTE)}m ago`;
  }
  if (elapsed < DAY) {
    return `${Math.floor(elapsed / HOUR)}h ago`;
  }
  if (elapsed < MONTH) {
    return `${Math.floor(elapsed / DAY)}d ago`;
  }
  return new Date(at).toISOString().slice(0, 10);
}

function isNotification(value: unknown): value is NotificationView {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate.id === "string"
    && typeof candidate.summary === "string"
    && typeof candidate.event_type === "string"
    && typeof candidate.occurred_at === "string"
    && typeof candidate.read === "boolean";
}
