import { StrictMode, useCallback, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import type { PageContext } from "../shared/context";
import { callApi, requestPageContext } from "../shared/messages";
import { describeSettingsProblem, loadSettings, type Settings } from "../shared/settings";
import "../shared/ui.css";

type Connection =
  | { state: "checking" }
  | { state: "unconfigured"; problem: string }
  | { state: "connected"; pending: number }
  | { state: "failed"; error: string };

type Page = { state: "reading" } | { state: "none"; reason: string } | { state: "read"; context: PageContext };

function SidePanel() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [connection, setConnection] = useState<Connection>({ state: "checking" });
  const [page, setPage] = useState<Page>({ state: "reading" });

  const refresh = useCallback(async () => {
    setConnection({ state: "checking" });
    setPage({ state: "reading" });

    const current = await loadSettings();
    setSettings(current);

    const result = await requestPageContext();
    setPage(result.supported ? { state: "read", context: result.context } : { state: "none", reason: result.reason });

    const problem = describeSettingsProblem(current);
    if (problem !== null) {
      setConnection({ state: "unconfigured", problem });
      return;
    }

    const actions = await callApi({ method: "GET", path: "/api/actions" });
    setConnection(
      actions.ok
        ? { state: "connected", pending: Array.isArray(actions.data) ? actions.data.length : 0 }
        : { state: "failed", error: actions.error }
    );
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return (
    <main>
      <h1>Pylos</h1>
      <p className="lede">{settings === null ? "Loading…" : settings.backendUrl}</p>

      {page.state === "reading" && <p className="status">Reading the page…</p>}
      {page.state === "none" && <p className="status">{page.reason}</p>}
      {page.state === "read" && (
        <div className="status good">
          <b>{page.context.site_type}</b> · {page.context.title}
          <p className="hint">{page.context.text.length} characters read from this page</p>
          <pre>{page.context.text}</pre>
        </div>
      )}

      {connection.state === "unconfigured" && <p className="status bad">{connection.problem}</p>}
      {connection.state === "failed" && <p className="status bad">{connection.error}</p>}
      {connection.state === "connected" && (
        <p className="hint">Backend reachable · {connection.pending} action(s) pending</p>
      )}

      <p>
        <button onClick={() => void refresh()}>Refresh</button>
        <button onClick={() => void chrome.runtime.openOptionsPage()}>Settings</button>
      </p>

      <p className="hint">
        Asking for an invoice arrives in the next stage. Nothing here can move money on its own: every
        action is proposed, validated and approved by you.
      </p>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <SidePanel />
  </StrictMode>
);
