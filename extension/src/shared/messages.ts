export type ApiRequest = {
  kind: "pylos.api";
  method: "GET" | "POST";
  path: string;
  body?: unknown;
};

export type ApiResult =
  | { ok: true; status: number; data: unknown }
  | { ok: false; status: number; error: string };

export function isApiRequest(message: unknown): message is ApiRequest {
  return (
    typeof message === "object" &&
    message !== null &&
    (message as ApiRequest).kind === "pylos.api"
  );
}

export async function callApi(request: Omit<ApiRequest, "kind">): Promise<ApiResult> {
  return chrome.runtime.sendMessage({ kind: "pylos.api", ...request } satisfies ApiRequest);
}
