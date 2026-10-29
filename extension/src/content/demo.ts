import type { SiteType } from "../shared/context";

export const demoSiteType: SiteType = "DEMO";

export function matchesDemo(url: URL): boolean {
  return url.pathname === "/demo" || url.pathname.startsWith("/demo/");
}

export function readThread(document: Document): string {
  const thread = document.querySelector("#thread");
  if (thread === null) {
    return "";
  }

  const lines = Array.from(thread.querySelectorAll("li"))
    .map(readMessage)
    .filter((line) => line !== "");

  return lines.join("\n");
}

function readMessage(message: Element): string {
  const sender = message.querySelector("b")?.textContent?.trim() ?? "";

  const body = Array.from(message.childNodes)
    .filter((node) => !isElement(node, "b") && !isElement(node, "time"))
    .map((node) => node.textContent ?? "")
    .join(" ");

  const text = collapse(body);
  if (text === "") {
    return "";
  }
  return sender === "" ? text : `${sender}: ${text}`;
}

function isElement(node: Node, name: string): boolean {
  return node.nodeType === 1 && (node as Element).tagName.toLowerCase() === name;
}

export function collapse(value: string): string {
  return value.replace(/\s+/g, " ").trim();
}
