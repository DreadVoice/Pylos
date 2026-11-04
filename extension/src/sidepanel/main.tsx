import { StrictMode, useCallback, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import type { ActionView, AssistResponse } from "../shared/actions";
import type { PageContext } from "../shared/context";
import { callApi, refreshBadge, requestPageContext } from "../shared/messages";
import {
  emptyNotifications,
  readNotifications,
  type NotificationsView
} from "../shared/notifications";
import { describeSettingsProblem, loadSettings } from "../shared/settings";
import { ConfirmCard, type Decision } from "./ConfirmCard";
import { Notifications } from "./Notifications";
import "../shared/ui.css";

const POLL_MS = 30_000;

const INVOICE_TASK = "Create the invoice we agreed in this conversation.";
const REFUND_TASK = "Refund what we agreed on this order.";

type Page = { state: "reading" } | { state: "none"; reason: string } | { state: "read"; context: PageContext };

function SidePanel() {
  const [problem, setProblem] = useState<string | null>(null);
  const [page, setPage] = useState<Page>({ state: "reading" });

  const [instruction, setInstruction] = useState(INVOICE_TASK);
  const [asking, setAsking] = useState(false);
  const [reply, setReply] = useState<string | null>(null);
  const [truncated, setTruncated] = useState(false);
  const [askError, setAskError] = useState<string | null>(null);
  const [actions, setActions] = useState<ActionView[]>([]);
  const [notifications, setNotifications] = useState<NotificationsView>(emptyNotifications());
  const [marking, setMarking] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  const refresh = useCallback(async () => {
    setProblem(describeSettingsProblem(await loadSettings()));

    setPage({ state: "reading" });
    const context = await requestPageContext();
    setPage(
      context.supported
        ? { state: "read", context: context.context }
        : { state: "none", reason: context.reason }
    );
    if (context.supported) {
      setInstruction(context.context.hints.invoice_ids.length > 0 ? REFUND_TASK : INVOICE_TASK);
    }

    const pending = await callApi({ method: "GET", path: "/api/actions?status=PROPOSED" });
    if (pending.ok && Array.isArray(pending.data)) {
      setActions(pending.data as ActionView[]);
    }

    await loadNotifications();
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    const timer = setInterval(() => {
      setNow(Date.now());
      void loadNotifications();
    }, POLL_MS);
    return () => clearInterval(timer);
  }, []);

  async function loadNotifications() {
    const result = await callApi({ method: "GET", path: "/api/notifications" });
    if (result.ok) {
      setNotifications(readNotifications(result.data));
    }
  }

  async function markRead() {
    setMarking(true);
    const result = await callApi({ method: "POST", path: "/api/notifications/mark-read" });
    setMarking(false);
    if (result.ok) {
      setNotifications(readNotifications(result.data));
      await refreshBadge();
    }
  }

  async function ask() {
    if (page.state !== "read") {
      return;
    }
    setAsking(true);
    setAskError(null);
    setReply(null);

    const result = await callApi({
      method: "POST",
      path: "/api/assist",
      body: { context: page.context, instruction }
    });
    setAsking(false);

    if (!result.ok) {
      setAskError(result.error);
      return;
    }

    const response = result.data as AssistResponse;
    setReply(response.message);
    setTruncated(response.context_truncated);
    setActions((existing) => [...response.proposed_actions, ...existing]);
  }

  async function decide(action: ActionView, path: string, body?: unknown): Promise<Decision> {
    const result = await callApi({ method: "POST", path, body });
    if (result.ok) {
      const updated = result.data as ActionView;
      setActions((existing) => existing.map((one) => (one.id === action.id ? updated : one)));
      return { ok: true, action: updated };
    }
    return { ok: false, error: result.error, violations: result.violations };
  }

  return (
    <main>
      <h1>Pylos</h1>

      {page.state === "reading" && <p className="lede">Reading the page…</p>}
      {page.state === "none" && <p className="lede">{page.reason}</p>}
      {page.state === "read" && (
        <p className="lede">
          <b>{page.context.site_type}</b> · {page.context.title}
        </p>
      )}

      {problem !== null && (
        <p className="status bad">
          {problem} <a onClick={() => void chrome.runtime.openOptionsPage()}>Open settings</a>
        </p>
      )}

      <textarea
        aria-label="What should Pylos do"
        rows={3}
        value={instruction}
        disabled={asking || page.state !== "read" || problem !== null}
        onChange={(event) => setInstruction(event.target.value)}
      />

      <p className="actions">
        <button onClick={() => void ask()} disabled={asking || page.state !== "read" || problem !== null}>
          {asking ? "Thinking…" : "Ask Pylos"}
        </button>
        <button className="ghost" onClick={() => void refresh()} disabled={asking}>
          Refresh
        </button>
      </p>

      {asking && <p className="status">Reading the conversation. This can take a minute.</p>}
      {askError !== null && <p className="status bad">{askError}</p>}
      {reply !== null && <p className="status">{reply}</p>}
      {truncated && <p className="hint">That conversation was long, so only the first part was read.</p>}

      <Notifications view={notifications} now={now} busy={marking} onMarkRead={() => void markRead()} />

      {actions.length === 0 && !asking && (
        <p className="hint">No proposals yet. Ask Pylos to invoice the conversation.</p>
      )}

      {actions.map((action) => (
        <ConfirmCard
          key={action.id}
          action={action}
          onApprove={(payload) => decide(action, `/api/actions/${action.id}/approve`, { payload })}
          onReject={() => decide(action, `/api/actions/${action.id}/reject`)}
        />
      ))}

      <p className="hint">
        Pylos proposes; you approve. Every amount is checked by the backend before anything is sent, and
        nothing on a page can approve an action on your behalf.
      </p>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <SidePanel />
  </StrictMode>
);
