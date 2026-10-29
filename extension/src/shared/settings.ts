export type Settings = {
  backendUrl: string;
  apiToken: string;
};

export const DEFAULT_SETTINGS: Settings = {
  backendUrl: "http://localhost:8080",
  apiToken: ""
};

const STORAGE_KEY = "pylos.settings";

export function normaliseBackendUrl(value: string): string {
  return value.trim().replace(/\/+$/, "");
}

export function describeSettingsProblem(settings: Settings): string | null {
  const url = normaliseBackendUrl(settings.backendUrl);
  if (url === "") {
    return "Set the backend URL, for example http://localhost:8080";
  }

  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return "The backend URL is not a valid URL";
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    return "The backend URL must start with http:// or https://";
  }
  if (settings.apiToken.trim() === "") {
    return "Set the API token that the backend was started with";
  }
  return null;
}

export function apiUrl(settings: Settings, path: string): string {
  const base = normaliseBackendUrl(settings.backendUrl);
  return `${base}${path.startsWith("/") ? path : `/${path}`}`;
}

export async function loadSettings(): Promise<Settings> {
  const stored = await chrome.storage.local.get(STORAGE_KEY);
  return { ...DEFAULT_SETTINGS, ...(stored[STORAGE_KEY] as Partial<Settings> | undefined) };
}

export async function saveSettings(settings: Settings): Promise<void> {
  await chrome.storage.local.set({
    [STORAGE_KEY]: {
      backendUrl: normaliseBackendUrl(settings.backendUrl),
      apiToken: settings.apiToken.trim()
    }
  });
}
