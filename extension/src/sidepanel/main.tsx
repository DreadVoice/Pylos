import { StrictMode, useCallback, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { callApi } from "../shared/messages";
import { describeSettingsProblem, loadSettings, type Settings } from "../shared/settings";
import "../shared/ui.css";

type Connection =
  | { state: "checking" }
  | { state: "unconfigured"; problem: string }
  | { state: "connected"; pending: number }
  | { state: "failed"; error: string };

function SidePanel() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [connection, setConnection] = useState<Connection>({ state: "checking" });

  const check = useCallback(async () => {
    setConnection({ state: "checking" });
    const current = await loadSettings();
    setSettings(current);

    const problem = describeSettingsProblem(current);
    if (problem !== null) {
      setConnection({ state: "unconfigured", problem });
      return;
    }

    const result = await callApi({ method: "GET", path: "/api/actions" });
    if (result.ok) {
      setConnection({ state: "connected", pending: Array.isArray(result.data) ? result.data.length : 0 });
    } else {
      setConnection({ state: "failed", error: result.error });
    }
  }, []);

  useEffect(() => {
    void check();
  }, [check]);

  return (
    <main>
      <h1>Pylos</h1>
      <p className="lede">{settings === null ? "Loading…" : settings.backendUrl}</p>

      {connection.state === "checking" && <p className="status">Checking the backend…</p>}

      {connection.state === "unconfigured" && (
        <p className="status bad">
          {connection.problem}{" "}
          <a href="#" onClick={() => void chrome.runtime.openOptionsPage()}>
            Open settings
          </a>
        </p>
      )}

      {connection.state === "connected" && (
        <p className="status good">Connected. {connection.pending} action(s) pending.</p>
      )}

      {connection.state === "failed" && <p className="status bad">{connection.error}</p>}

      <p>
        <button onClick={() => void check()}>Check again</button>
        <button onClick={() => void chrome.runtime.openOptionsPage()}>Settings</button>
      </p>

      <p className="hint">
        Reading the page and proposing invoices arrives in the next stage. Nothing here can move money on
        its own: every action is proposed, validated and approved by you.
      </p>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <SidePanel />
  </StrictMode>
);
