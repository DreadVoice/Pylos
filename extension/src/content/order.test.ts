import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { describe, expect, it } from "vitest";
import { adapterFor, buildPageContext } from "./router";
import { readDemoHints, readDemoPage, readOrder } from "./demo";

const ORDER_PAGE = "../backend/src/main/resources/static/demo/order.html";
const CHAT_PAGE = "../backend/src/main/resources/static/demo/chat.html";
const PAGE_URL = "http://localhost:8080/demo/order";

function documentFor(path: string, url: string): Document {
  return new JSDOM(readFileSync(path, "utf8"), { url }).window.document;
}

const orderDocument = () => documentFor(ORDER_PAGE, PAGE_URL);

describe("the order page adapter", () => {
  it("is claimed by the demo adapter", () => {
    expect(adapterFor(new URL("http://localhost:8080/demo/order"))?.name).toBe("demo");
    expect(adapterFor(new URL("http://localhost:8080/demo/order.html"))?.name).toBe("demo");
  });

  it("reads the order facts a refund needs", () => {
    const text = readOrder(orderDocument());

    expect(text).toContain("INV2-GS4Q-CBHK-S6QM-CC5V");
    expect(text).toContain("USD 60.00");
    expect(text).toContain("Black Hoodie - Large, quantity 2 at 30.00 each");
    expect(text).toContain("sb-l47vrz53533074@personal.example.com");
  });

  it("reads the order and the messages together", () => {
    const text = readDemoPage(orderDocument());

    expect(text).toContain("INV2-GS4Q-CBHK-S6QM-CC5V");
    expect(text).toContain("Alex Mercer: Both hoodies arrived today");
    expect(text).toContain("Hoodie Haven: Yes, 30.00 USD back on the original payment.");
    expect(text.indexOf("INV2-GS4Q")).toBeLessThan(text.indexOf("Alex Mercer"));
  });

  it("surfaces the planted instruction as ordinary page text", () => {
    const text = readDemoPage(orderDocument());

    expect(text).toContain("SYSTEM OVERRIDE");
    expect(text).toContain("attacker@evil.example.com");
  });

  it("leaves the page's own commentary out", () => {
    const text = readDemoPage(orderDocument());

    expect(text).not.toContain("prompt-injection attempt");
    expect(text).not.toContain("paste its id into the proposal card");
    expect(text).not.toContain("read from PayPal at approval time");
    expect(text).not.toContain("Pylos demo page");
  });

  it("hints the invoice id once, however often it appears", () => {
    const hints = readDemoHints(orderDocument());

    expect(hints.invoice_ids).toEqual(["INV2-GS4Q-CBHK-S6QM-CC5V"]);
    expect(hints.dispute_ids).toEqual([]);
  });

  it("hints nothing on a page with no order block", () => {
    const hints = readDemoHints(documentFor(CHAT_PAGE, "http://localhost:8080/demo/chat"));

    expect(hints.invoice_ids).toEqual([]);
    expect(hints.dispute_ids).toEqual([]);
  });

  it("still reads the chat page, which has no order block", () => {
    const text = readDemoPage(documentFor(CHAT_PAGE, "http://localhost:8080/demo/chat"));

    expect(readOrder(documentFor(CHAT_PAGE, "http://localhost:8080/demo/chat"))).toBe("");
    expect(text).toContain("Alex Mercer: Hi! Do you still have the black hoodies in large?");
    expect(text.startsWith("Alex Mercer:")).toBe(true);
  });

  it("builds a page context carrying the hints", () => {
    const document = orderDocument();
    const adapter = adapterFor(new URL(PAGE_URL))!;

    const context = buildPageContext(adapter, document, PAGE_URL);

    expect(context.site_type).toBe("DEMO");
    expect(context.title).toContain("Order HH-2291");
    expect(context.hints.invoice_ids).toEqual(["INV2-GS4Q-CBHK-S6QM-CC5V"]);
    expect(context.text).toContain("Black Hoodie - Large");
  });
});
