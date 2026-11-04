import {
  describeAmount,
  timeAgo,
  type NotificationsView,
  type NotificationView
} from "../shared/notifications";

const SHOWN = 8;

type Props = {
  view: NotificationsView;
  now: number;
  busy: boolean;
  onMarkRead: () => void;
};

export function Notifications({ view, now, busy, onMarkRead }: Props) {
  if (view.notifications.length === 0) {
    return null;
  }

  return (
    <section className="card">
      <header>
        <b>From PayPal</b>
        {view.unread_count > 0 && <span className="tag tag-new">{view.unread_count} new</span>}
      </header>

      <ul className="feed">
        {view.notifications.slice(0, SHOWN).map((notification) => (
          <Line key={notification.id} notification={notification} now={now} />
        ))}
      </ul>

      {view.unread_count > 0 && (
        <p className="actions">
          <button className="ghost" onClick={onMarkRead} disabled={busy}>
            {busy ? "Marking…" : "Mark all read"}
          </button>
        </p>
      )}
    </section>
  );
}

function Line({ notification, now }: { notification: NotificationView; now: number }) {
  const amount = describeAmount(notification);
  const when = timeAgo(notification.occurred_at, now);

  return (
    <li className={notification.read ? "seen" : undefined}>
      <span>{notification.summary}</span>
      {amount !== null && <b className="money">{amount}</b>}
      {when !== "" && <span className="when">{when}</span>}
    </li>
  );
}
