import { useState } from "react";
import {
  emptyItem,
  type ActionView,
  type InvoiceItem,
  type InvoicePayload,
  type ReminderPayload
} from "../shared/actions";
import { describeTotal } from "../shared/money";

export type Decision = {
  ok: boolean;
  action?: ActionView;
  error?: string;
  violations?: string[];
};

type Props = {
  action: ActionView;
  onApprove: (payload: unknown) => Promise<Decision>;
  onReject: () => Promise<Decision>;
};

export function ConfirmCard({ action, onApprove, onReject }: Props) {
  const [current, setCurrent] = useState(action);
  const [payload, setPayload] = useState(action.payload);
  const [busy, setBusy] = useState<"approve" | "reject" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [violations, setViolations] = useState<string[]>([]);

  const settled = current.status !== "PROPOSED";

  async function decide(what: "approve" | "reject") {
    setBusy(what);
    setError(null);
    setViolations([]);

    const decision = what === "approve" ? await onApprove(payload) : await onReject();
    setBusy(null);

    if (decision.ok && decision.action !== undefined) {
      setCurrent(decision.action);
      return;
    }
    setError(decision.error ?? "That did not work.");
    setViolations(decision.violations ?? []);
  }

  return (
    <section className="card">
      <header>
        <b>{current.summary}</b>
        <span className={`tag tag-${current.status.toLowerCase()}`}>{current.status}</span>
      </header>

      {settled ? (
        <Settled action={current} />
      ) : (
        <Editor
          action={current}
          payload={payload}
          onChange={setPayload}
          disabled={busy !== null}
        />
      )}

      {error !== null && (
        <div className="status bad">
          {error}
          {violations.length > 0 && (
            <ul>
              {violations.map((violation) => (
                <li key={violation}>{violation}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      {!settled && (
        <p className="actions">
          <button onClick={() => void decide("approve")} disabled={busy !== null}>
            {busy === "approve" ? "Sending…" : "Approve and send"}
          </button>
          <button className="ghost" onClick={() => void decide("reject")} disabled={busy !== null}>
            {busy === "reject" ? "Rejecting…" : "Reject"}
          </button>
        </p>
      )}
    </section>
  );
}

function Settled({ action }: { action: ActionView }) {
  if (action.status === "EXECUTED") {
    return <p className="status good">Sent. PayPal reference {action.result_reference}</p>;
  }
  if (action.status === "REJECTED") {
    return <p className="status">Rejected. Nothing was sent.</p>;
  }
  return <p className="status bad">{action.error_message ?? "That action failed."}</p>;
}

type EditorProps = {
  action: ActionView;
  payload: ActionView["payload"];
  onChange: (payload: ActionView["payload"]) => void;
  disabled: boolean;
};

function Editor({ action, payload, onChange, disabled }: EditorProps) {
  if (action.action_type === "SEND_INVOICE") {
    return (
      <InvoiceEditor
        payload={(payload ?? {}) as InvoicePayload}
        onChange={onChange}
        disabled={disabled}
      />
    );
  }
  if (action.action_type === "SEND_REMINDER") {
    return (
      <ReminderEditor
        payload={(payload ?? {}) as ReminderPayload}
        onChange={onChange}
        disabled={disabled}
      />
    );
  }
  return <pre>{JSON.stringify(payload, null, 2)}</pre>;
}

function InvoiceEditor({
  payload,
  onChange,
  disabled
}: {
  payload: InvoicePayload;
  onChange: (payload: InvoicePayload) => void;
  disabled: boolean;
}) {
  const items = payload.items ?? [];

  function setItem(index: number, item: InvoiceItem) {
    onChange({ ...payload, items: items.map((existing, at) => (at === index ? item : existing)) });
  }

  return (
    <>
      <label>
        <span>Buyer email</span>
        <input
          value={payload.recipient_email ?? ""}
          disabled={disabled}
          onChange={(event) => onChange({ ...payload, recipient_email: event.target.value })}
        />
      </label>

      <label>
        <span>Currency</span>
        <input
          value={payload.currency_code ?? ""}
          disabled={disabled}
          onChange={(event) => onChange({ ...payload, currency_code: event.target.value })}
        />
      </label>

      {items.map((item, index) => (
        <div className="item" key={index}>
          <input
            aria-label={`Item ${index + 1} name`}
            value={item.name ?? ""}
            disabled={disabled}
            onChange={(event) => setItem(index, { ...item, name: event.target.value })}
          />
          <input
            aria-label={`Item ${index + 1} quantity`}
            className="narrow"
            value={item.quantity ?? ""}
            disabled={disabled}
            onChange={(event) => setItem(index, { ...item, quantity: event.target.value })}
          />
          <input
            aria-label={`Item ${index + 1} unit amount`}
            className="narrow"
            value={item.unit_amount ?? ""}
            disabled={disabled}
            onChange={(event) => setItem(index, { ...item, unit_amount: event.target.value })}
          />
          <button
            className="ghost"
            aria-label={`Remove item ${index + 1}`}
            disabled={disabled}
            onClick={() => onChange({ ...payload, items: items.filter((_, at) => at !== index) })}
          >
            ×
          </button>
        </div>
      ))}

      <p className="actions">
        <button
          className="ghost"
          disabled={disabled}
          onClick={() => onChange({ ...payload, items: [...items, emptyItem()] })}
        >
          Add item
        </button>
        <span className="total">{describeTotal(payload.currency_code, items)}</span>
      </p>

      <label>
        <span>Note</span>
        <input
          value={payload.note ?? ""}
          disabled={disabled}
          onChange={(event) => onChange({ ...payload, note: event.target.value })}
        />
      </label>
    </>
  );
}

function ReminderEditor({
  payload,
  onChange,
  disabled
}: {
  payload: ReminderPayload;
  onChange: (payload: ReminderPayload) => void;
  disabled: boolean;
}) {
  return (
    <>
      <label>
        <span>Invoice</span>
        <input
          value={payload.invoice_id ?? ""}
          disabled={disabled}
          onChange={(event) => onChange({ ...payload, invoice_id: event.target.value })}
        />
      </label>
      <label>
        <span>Subject</span>
        <input
          value={payload.subject ?? ""}
          disabled={disabled}
          onChange={(event) => onChange({ ...payload, subject: event.target.value })}
        />
      </label>
      <label>
        <span>Note</span>
        <input
          value={payload.note ?? ""}
          disabled={disabled}
          onChange={(event) => onChange({ ...payload, note: event.target.value })}
        />
      </label>
    </>
  );
}
