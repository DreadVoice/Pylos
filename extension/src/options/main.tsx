import { StrictMode, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { callApi } from "../shared/messages";
import {
  DEFAULT_SETTINGS,
  describeSettingsProblem,
  loadSettings,
  saveSettings,
  type Settings
} from "../shared/settings";
import "../shared/ui.css";

type Status = { tone: "good" | "bad"; text: string } | null;

function Options() {
  const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS);
  const [status, setStatus] = useState<Status>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void loadSettings().then(setSettings);
  }, []);

  const problem = describeSettingsProblem(settings);

  async function save() {
    await saveSettings(settings);
    setStatus({ tone: "good", text: "Saved." });
  }

  async function testConnection() {
    setBusy(true);
    setStatus(null);
    await saveSettings(settings);
    const result = await callApi({ method: "GET", path: "/api/actions" });
    setBusy(false);

    if (result.ok) {
      const count = Array.isArray(result.data) ? result.data.length : 0;
      setStatus({ tone: "good", text: `Connected. ${count} action(s) pending.` });
    } else {
      setStatus({ tone: "bad", text: result.error });
    }
  }

  return (
    <main>
      <h1>Pylos settings</h1>
      <p className="lede">Point the extension at your Pylos backend.</p>

      <label>
        <span>Backend URL</span>
        <input
          value={settings.backendUrl}
          onChange={(event) => setSettings({ ...settings, backendUrl: event.target.value })}
          placeholder="http://localhost:8080"
        />
      </label>

      <label>
        <span>API token</span>
        <input
          type="password"
          value={settings.apiToken}
          onChange={(event) => setSettings({ ...settings, apiToken: event.target.value })}
          placeholder="PYLOS_API_TOKEN"
        />
      </label>

      <button onClick={() => void save()} disabled={problem !== null}>
        Save
      </button>
      <button onClick={() => void testConnection()} disabled={problem !== null || busy}>
        {busy ? "Testing…" : "Test connection"}
      </button>

      {problem !== null && <p className="status bad">{problem}</p>}
      {problem === null && status !== null && (
        <p className={`status ${status.tone}`}>{status.text}</p>
      )}

      <p className="hint">
        The token is the value the backend was started with as PYLOS_API_TOKEN. It is stored only in this
        browser and is never sent anywhere except your backend.
      </p>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Options />
  </StrictMode>
);
