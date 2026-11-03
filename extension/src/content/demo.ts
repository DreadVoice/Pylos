import { emptyHints, type PageHints, type SiteType } from "../shared/context";

export const demoSiteType: SiteType = "DEMO";

const INVOICE_ID = /INV2-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}/g;

export function matchesDemo(url: URL): boolean {
  return url.pathname === "/demo" || url.pathname.startsWith("/demo/");
}

export function readDemoPage(document: Document): string {
  return [readOrder(document), readThread(document)].filter((part) => part !== "").join("\n");
}

export function readDemoHints(document: Document): PageHints {
  const order = document.querySelector("#order");
  if (order === null) {
    return emptyHints();
  }

  const found = (order.textContent ?? "").match(INVOICE_ID) ?? [];
  return { invoice_ids: Array.from(new Set(found)), dispute_ids: [] };
}

export function readOrder(document: Document): string {
  const order = document.querySelector("#order");
  if (order === null) {
    return "";
  }

  return Array.from(order.querySelectorAll("li"))
    .map((row) => collapse(row.textContent ?? ""))
    .filter((line) => line !== "")
    .join("\n");
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
