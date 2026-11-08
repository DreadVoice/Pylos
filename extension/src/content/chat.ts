import { emptyHints, type PageHints } from "../shared/context";
import { collapse } from "./demo";

const INVOICE_ID = /INV2-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}/g;
const MAX_MESSAGES = 80;
const MAX_SENDER = 80;

export type ChatMessage = {
  sender: string;
  text: string;
  outgoing: boolean;
};

export type ChatReader = (document: Document) => ChatMessage[];

export function formatChat(messages: ChatMessage[]): string {
  return messages
    .slice(-MAX_MESSAGES)
    .filter((message) => message.text !== "")
    .map((message) => `${speaker(message)}${message.text}`)
    .join("\n");
}

function speaker(message: ChatMessage): string {
  if (message.sender === "") {
    return message.outgoing ? "The seller: " : "";
  }
  return message.outgoing ? `${message.sender} (the seller): ` : `${message.sender}: `;
}

export function chatHints(messages: ChatMessage[]): PageHints {
  const text = messages.map((message) => message.text).join(" ");
  const found = text.match(INVOICE_ID) ?? [];
  if (found.length === 0) {
    return emptyHints();
  }
  return { invoice_ids: Array.from(new Set(found)), dispute_ids: [] };
}

export function readWhatsApp(document: Document): ChatMessage[] {
  const rows = Array.from(document.querySelectorAll("[data-pre-plain-text]"));

  return rows.map((row) => {
    const meta = row.getAttribute("data-pre-plain-text") ?? "";
    const spoken = row.querySelector('span.selectable-text, [data-testid="selectable-text"]');

    return {
      sender: senderFromWhatsAppMeta(meta),
      text: collapse(spoken?.textContent ?? ""),
      outgoing: whatsAppOutgoing(row)
    };
  });
}

function whatsAppOutgoing(row: Element): boolean {
  const legacy = row.closest("div.message-in, div.message-out");
  if (legacy !== null) {
    return legacy.classList.contains("message-out");
  }

  const identified = row.closest("[data-id]")?.getAttribute("data-id") ?? "";
  if (identified.startsWith("true_")) {
    return true;
  }
  if (identified.startsWith("false_")) {
    return false;
  }

  const container = row.closest('div[role="row"]') ?? row.parentElement;
  return container?.querySelector('[data-testid="msg-meta"] [aria-label]') != null;
}

export function readDiscord(document: Document): ChatMessage[] {
  const rows = Array.from(document.querySelectorAll('li[id^="chat-messages-"]'));
  let lastSender = "";

  return rows.map((row) => {
    const named = collapse(row.querySelector('[class*="username"]')?.textContent ?? "");
    if (named !== "") {
      lastSender = named;
    }
    const body = row.querySelector('[id^="message-content-"]');

    return {
      sender: trimSender(lastSender),
      text: collapse(body?.textContent ?? ""),
      outgoing: false
    };
  });
}

export function readMessenger(document: Document): ChatMessage[] {
  const rows = Array.from(document.querySelectorAll('div[role="row"]'));

  return rows.map((row) => {
    const body = row.querySelector('div[dir="auto"]');

    return {
      sender: trimSender(senderFromMessengerRow(row)),
      text: collapse(body?.textContent ?? ""),
      outgoing: false
    };
  });
}

function senderFromWhatsAppMeta(meta: string): string {
  const closingBracket = meta.indexOf("] ");
  if (closingBracket === -1) {
    return "";
  }
  const afterTime = meta.slice(closingBracket + 2);
  const colon = afterTime.lastIndexOf(":");
  return trimSender(collapse(colon === -1 ? afterTime : afterTime.slice(0, colon)));
}

function senderFromMessengerRow(row: Element): string {
  const label = row.getAttribute("aria-label") ?? "";
  const match = /^(.+?) sent /.exec(collapse(label));
  if (match !== null) {
    return match[1]!;
  }
  return collapse(row.querySelector("h4, h5")?.textContent ?? "");
}

function trimSender(sender: string): string {
  const trimmed = collapse(sender);
  return trimmed.length > MAX_SENDER ? trimmed.slice(0, MAX_SENDER) : trimmed;
}
