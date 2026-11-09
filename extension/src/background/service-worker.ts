import {
  isApiRequest,
  isBadgeRequest,
  isContextRequest,
  type ApiRequest,
  type ApiResult,
  type ContextResult,
  type ExtractRequest
} from "../shared/messages";
import { apiUrl, describeSettingsProblem, loadSettings } from "../shared/settings";
import { badgeText, readNotifications } from "../shared/notifications";

const POLL_ALARM = "pylos.notifications";
const POLL_MINUTES = 1;
const BADGE_COLOUR = "#2f5d94";

chrome.runtime.onInstalled.addListener(() => {
  chrome.sidePanel
    .setPanelBehavior({ openPanelOnActionClick: true })
    .catch((error: unknown) => console.warn("Pylos: could not set panel behaviour", error));
  startPolling();
  void reportConnection();
  void updateBadge();
});

chrome.runtime.onStartup.addListener(() => {
  startPolling();
  void reportConnection();
  void updateBadge();
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === POLL_ALARM) {
    void updateBadge();
  }
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (isApiRequest(message)) {
    request(message).then(sendResponse, (error: unknown) =>
      sendResponse({ ok: false, status: 0, error: String(error) } satisfies ApiResult)
    );
    return true;
  }
  if (isBadgeRequest(message)) {
    updateBadge().then(
      () => sendResponse({ ok: true }),
      (error: unknown) => sendResponse({ ok: false, error: String(error) })
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

function startPolling(): void {
  chrome.alarms.create(POLL_ALARM, { periodInMinutes: POLL_MINUTES });
}

async function updateBadge(): Promise<void> {
  const result = await request({
    kind: "pylos.api",
    method: "GET",
    path: "/api/notifications?unread=true"
  });

  const unread = result.ok ? readNotifications(result.data).unread_count : 0;
  await chrome.action.setBadgeBackgroundColor({ color: BADGE_COLOUR });
  await chrome.action.setBadgeText({ text: badgeText(unread) });
}

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
    return {
      ok: false,
      status: response.status,
      error: describeFailure(response.status, data),
      violations: readViolations(data)
    };
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

function readViolations(data: unknown): string[] | undefined {
  if (typeof data !== "object" || data === null) {
    return undefined;
  }
  const violations = (data as { violations?: unknown }).violations;
  if (!Array.isArray(violations)) {
    return undefined;
  }
  return violations.filter((violation): violation is string => typeof violation === "string");
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
  const result = await request({
    kind: "pylos.api",
    method: "GET",
    path: "/api/actions?status=PROPOSED"
  });
  if (result.ok) {
    const count = Array.isArray(result.data) ? result.data.length : 0;
    console.info(`Pylos: connected to the backend, ${count} proposal(s) awaiting approval`);
  } else {
    console.warn(`Pylos: not connected — ${result.error}`);
  }
}
