import type { PageContext } from "./context";

export type ApiRequest = {
  kind: "pylos.api";
  method: "GET" | "POST";
  path: string;
  body?: unknown;
};

export type ApiResult =
  | { ok: true; status: number; data: unknown }
  | { ok: false; status: number; error: string; violations?: string[] };

export type ContextRequest = { kind: "pylos.context" };

export type ExtractRequest = { kind: "pylos.extract" };

export type ContextResult =
  | { supported: true; context: PageContext }
  | { supported: false; reason: string };

export function isApiRequest(message: unknown): message is ApiRequest {
  return kindOf(message) === "pylos.api";
}

export function isContextRequest(message: unknown): message is ContextRequest {
  return kindOf(message) === "pylos.context";
}

function kindOf(message: unknown): string | null {
  if (typeof message !== "object" || message === null) {
    return null;
  }
  const kind = (message as { kind?: unknown }).kind;
  return typeof kind === "string" ? kind : null;
}

export async function callApi(request: Omit<ApiRequest, "kind">): Promise<ApiResult> {
  return chrome.runtime.sendMessage({ kind: "pylos.api", ...request } satisfies ApiRequest);
}

export async function requestPageContext(): Promise<ContextResult> {
  return chrome.runtime.sendMessage({ kind: "pylos.context" } satisfies ContextRequest);
}
