import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { describe, expect, it } from "vitest";
import { chatHints, formatChat, readDiscord, readMessenger, readWhatsApp } from "./chat";
import { adapterFor, buildPageContext } from "./router";

function fixture(name: string, url: string): Document {
  const html = readFileSync(`src/content/fixtures/${name}.html`, "utf8");
  return new JSDOM(html, { url }).window.document;
}

const WHATSAPP_URL = "https://web.whatsapp.com/";
const DISCORD_URL = "https://discord.com/channels/998877/112233";
const MESSENGER_URL = "https://www.messenger.com/t/123456";

describe("which adapter claims which chat", () => {
  it("claims the three chats it has readers for", () => {
    expect(adapterFor(new URL(WHATSAPP_URL))?.name).toBe("whatsapp");
    expect(adapterFor(new URL(DISCORD_URL))?.name).toBe("discord");
    expect(adapterFor(new URL(MESSENGER_URL))?.name).toBe("messenger");
    expect(adapterFor(new URL("https://www.facebook.com/messages/t/99"))?.name).toBe("messenger");
  });

  it("claims nothing outside a conversation", () => {
    expect(adapterFor(new URL("https://discord.com/"))).toBeNull();
    expect(adapterFor(new URL("https://discord.com/login"))).toBeNull();
    expect(adapterFor(new URL("https://www.messenger.com/"))).toBeNull();
    expect(adapterFor(new URL("https://www.facebook.com/marketplace/item/1"))).toBeNull();
    expect(adapterFor(new URL("https://whatsapp.com/"))).toBeNull();
    expect(adapterFor(new URL("https://evil.example.com/web.whatsapp.com/"))).toBeNull();
  });

  it("reads every chat as the CHAT site type", () => {
    for (const url of [WHATSAPP_URL, DISCORD_URL, MESSENGER_URL]) {
      expect(adapterFor(new URL(url))?.siteType).toBe("CHAT");
    }
  });
});

describe("WhatsApp Web", () => {
  const document = () => fixture("whatsapp", WHATSAPP_URL);

  it("reads each bubble as sender and text", () => {
    const text = formatChat(readWhatsApp(document()));

    expect(text).toContain("Alex Mercer: Hi! Do you still have the black hoodies in large?");
    expect(text).toContain("Hoodie Haven: Hey Alex, yes, large is back in stock. They are 25.00 USD each.");
    expect(text).toContain("Alex Mercer: Perfect, can I take two of them please?");
    expect(text).toContain("sb-l47vrz53533074@personal.example.com");
  });

  it("knows which side the seller is on", () => {
    const messages = readWhatsApp(document());

    expect(messages[0]!.outgoing).toBe(false);
    expect(messages[1]!.outgoing).toBe(true);
    expect(messages[1]!.sender).toBe("Hoodie Haven");
  });

  it("passes a planted instruction through as ordinary text", () => {
    expect(formatChat(readWhatsApp(document()))).toContain("SYSTEM OVERRIDE");
  });

  it("hints the invoice id mentioned in the thread", () => {
    expect(chatHints(readWhatsApp(document())).invoice_ids)
      .toEqual(["INV2-GS4Q-CBHK-S6QM-CC5V"]);
  });

  it("builds a page context the backend will accept", () => {
    const context = buildPageContext(
      adapterFor(new URL(WHATSAPP_URL))!, document(), WHATSAPP_URL);

    expect(context.site_type).toBe("CHAT");
    expect(context.title).toBe("Alex Mercer (2) - WhatsApp");
    expect(context.text).toContain("25.00 USD each");
    expect(context.hints.invoice_ids).toEqual(["INV2-GS4Q-CBHK-S6QM-CC5V"]);
  });
});

describe("Discord", () => {
  const document = () => fixture("discord", DISCORD_URL);

  it("reads each message as sender and text", () => {
    const text = formatChat(readDiscord(document()));

    expect(text).toContain("Alex Mercer: Hi! Do you still have the black hoodies in large?");
    expect(text).toContain("Hoodie Haven: Yes, large is back in stock. They are 25.00 USD each.");
  });

  it("carries the sender forward across grouped messages", () => {
    const messages = readDiscord(document());

    expect(messages[2]!.text).toBe("Shall I send an invoice?");
    expect(messages[2]!.sender).toBe("Hoodie Haven");
  });
});

describe("Messenger", () => {
  const document = () => fixture("messenger", MESSENGER_URL);

  it("takes the sender from the row label", () => {
    const messages = readMessenger(document());

    expect(messages[0]!.sender).toBe("Alex Mercer");
    expect(messages[0]!.text).toBe("Is the black hoodie still available in large?");
    expect(messages[1]!.sender).toBe("Hoodie Haven");
  });

  it("falls back to a heading when a row carries no label", () => {
    const messages = readMessenger(document());

    expect(messages[3]!.sender).toBe("Hoodie Haven");
    expect(messages[3]!.text).toContain("INV2-GS4Q-CBHK-S6QM-CC5V");
  });
});

describe("when a chat does not look the way the reader expects", () => {
  const empty = (url: string) => new JSDOM("<html><body><div>nothing here</div></body></html>",
    { url }).window.document;

  it("reads nothing rather than guessing", () => {
    expect(formatChat(readWhatsApp(empty(WHATSAPP_URL)))).toBe("");
    expect(formatChat(readDiscord(empty(DISCORD_URL)))).toBe("");
    expect(formatChat(readMessenger(empty(MESSENGER_URL)))).toBe("");
  });

  it("produces an empty context, which the content script reports as unreadable", () => {
    const context = buildPageContext(
      adapterFor(new URL(DISCORD_URL))!, empty(DISCORD_URL), DISCORD_URL);

    expect(context.text).toBe("");
    expect(context.hints.invoice_ids).toEqual([]);
  });

  it("hints nothing when no invoice id appears", () => {
    expect(chatHints([{ sender: "A", text: "no ids here", outgoing: false }]).invoice_ids)
      .toEqual([]);
  });
});
