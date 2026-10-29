import {
  isApiRequest,
  isContextRequest,
  type ApiRequest,
  type ApiResult,
  type ContextResult,
  type ExtractRequest
} from "../shared/messages";
import { apiUrl, describeSettingsProblem, loadSettings } from "../shared/settings";

chrome.runtime.onInstalled.addListener(() => {
  chrome.sidePanel
    .setPanelBehavior({ openPanelOnActionClick: true })
    .catch((error: unknown) => console.warn("Pylos: could not set panel behaviour", error));
  void reportConnection();
});

chrome.runtime.onStartup.addListener(() => {
  void reportConnection();
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (isApiRequest(message)) {
    request(message).then(sendResponse, (error: unknown) =>
      sendResponse({ ok: false, status: 0, error: String(error) } satisfies ApiResult)
    );
    return true;
  }
  if (isContextRequest(message)) {
    readActivePage().then(sendResponse, (error: unknown) =>
      sendResponse({ supported: false, reason: String(error) } satisfies ContextResult)
    );
    return true;
  }
  return false;
});

async function readActivePage(): Promise<ContextResult> {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (tab?.id === undefined) {
    return { supported: false, reason: "No active tab to read." };
  }

  try {
    return await chrome.tabs.sendMessage(tab.id, { kind: "pylos.extract" } satisfies ExtractRequest);
  } catch {
    return { supported: false, reason: "Pylos does not read this page. Open a supported page." };
  }
}

async function request(message: ApiRequest): Promise<ApiResult> {
  const settings = await loadSettings();
  const problem = describeSettingsProblem(settings);
  if (problem !== null) {
    return { ok: false, status: 0, error: problem };
  }

  let response: Response;
  try {
    response = await fetch(apiUrl(settings, message.path), {
      method: message.method,
      headers: {
        Authorization: `Bearer ${settings.apiToken}`,
        ...(message.body === undefined ? {} : { "Content-Type": "application/json" })
      },
      body: message.body === undefined ? undefined : JSON.stringify(message.body)
    });
  } catch {
    return { ok: false, status: 0, error: `Could not reach ${settings.backendUrl}` };
  }

  const text = await response.text();
  const data = text === "" ? null : safeParse(text);

  if (!response.ok) {
    return { ok: false, status: response.status, error: describeFailure(response.status, data) };
  }
  return { ok: true, status: response.status, data };
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function describeFailure(status: number, data: unknown): string {
  if (status === 401) {
    return "The backend rejected the API token. Check it in the Pylos options page.";
  }
  const detail =
    typeof data === "object" && data !== null && typeof (data as { detail?: unknown }).detail === "string"
      ? (data as { detail: string }).detail
      : null;
  return detail ?? `The backend returned ${status}`;
}

async function reportConnection(): Promise<void> {
  const result = await request({ kind: "pylos.api", method: "GET", path: "/api/actions" });
  if (result.ok) {
    const count = Array.isArray(result.data) ? result.data.length : 0;
    console.info(`Pylos: connected to the backend, ${count} action(s) pending`);
  } else {
    console.warn(`Pylos: not connected — ${result.error}`);
  }
}
