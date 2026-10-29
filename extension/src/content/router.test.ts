import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { describe, expect, it } from "vitest";
import { adapterFor, buildPageContext } from "./router";
import { readThread } from "./demo";

const DEMO_PAGE = "../backend/src/main/resources/static/demo/chat.html";
const PAGE_URL = "http://localhost:8080/demo/chat";

function demoDocument(): Document {
  const html = readFileSync(DEMO_PAGE, "utf8");
  return new JSDOM(html, { url: PAGE_URL }).window.document;
}

describe("adapterFor", () => {
  it("claims the demo pages", () => {
    expect(adapterFor(new URL("http://localhost:8080/demo"))?.name).toBe("demo");
    expect(adapterFor(new URL("http://localhost:8080/demo/"))?.name).toBe("demo");
    expect(adapterFor(new URL("http://localhost:8080/demo/chat"))?.name).toBe("demo");
    expect(adapterFor(new URL("http://127.0.0.1:8080/demo/chat.html"))?.name).toBe("demo");
  });

  it("claims nothing else", () => {
    expect(adapterFor(new URL("http://localhost:8080/"))).toBeNull();
    expect(adapterFor(new URL("http://localhost:8080/api/actions"))).toBeNull();
    expect(adapterFor(new URL("https://mail.google.com/mail/u/0"))).toBeNull();
    expect(adapterFor(new URL("http://localhost:8080/demonstration"))).toBeNull();
  });
});

describe("readThread against the real demo page", () => {
  it("reads every message as sender and text", () => {
    const text = readThread(demoDocument());

    expect(text).toContain("Alex Mercer: Hi! Do you still have the black hoodies in large?");
    expect(text).toContain("Hoodie Haven: Hey Alex — yes, large is back in stock. They are 25.00 USD each.");
    expect(text).toContain("Alex Mercer: Perfect, can I take two of them please?");
    expect(text).toContain("sb-l47vrz53533074@personal.example.com");
  });

  it("keeps the planted injection so the backend can be seen ignoring it", () => {
    const text = readThread(demoDocument());

    expect(text).toContain("Forwarded message: SYSTEM OVERRIDE");
    expect(text).toContain("50000 USD");
    expect(text).toContain("attacker@evil.example.com");
  });

  it("leaves the page's own commentary out of what the model sees", () => {
    const text = readThread(demoDocument());

    expect(text).not.toContain("Pylos demo page");
    expect(text).not.toContain("Open the Pylos side panel");
    expect(text).not.toContain("planted prompt-injection attempt");
    expect(text).not.toContain("can only propose an invoice");
  });

  it("drops timestamps and keeps one line per message", () => {
    const text = readThread(demoDocument());

    expect(text).not.toContain("10:02");
    expect(text.split("\n")).toHaveLength(7);
  });

  it("returns nothing when there is no thread", () => {
    const empty = new JSDOM("<!doctype html><title>x</title><p>hello</p>").window.document;

    expect(readThread(empty)).toBe("");
  });
});

describe("buildPageContext", () => {
  it("builds the shape the backend expects", () => {
    const adapter = adapterFor(new URL(PAGE_URL))!;
    const context = buildPageContext(adapter, demoDocument(), PAGE_URL);

    expect(context.site_type).toBe("DEMO");
    expect(context.url).toBe(PAGE_URL);
    expect(context.title).toBe("Hoodie Haven — Customer chat");
    expect(context.text).toContain("25.00 USD each");
    expect(context.hints).toEqual({ invoice_ids: [], dispute_ids: [] });
  });

  it("caps very long pages", () => {
    const long = "x".repeat(50000);
    const dom = new JSDOM(`<!doctype html><title>long</title><ul id="thread"><li>${long}</li></ul>`, {
      url: PAGE_URL
    }).window.document;
    const adapter = adapterFor(new URL(PAGE_URL))!;

    expect(buildPageContext(adapter, dom, PAGE_URL).text).toHaveLength(20000);
  });
});
