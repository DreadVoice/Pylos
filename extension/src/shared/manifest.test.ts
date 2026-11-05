import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

type Manifest = {
  manifest_version: number;
  name: string;
  version: string;
  permissions: string[];
  host_permissions: string[];
  background: { service_worker: string; type: string };
  content_scripts: { matches: string[]; js: string[]; run_at: string }[];
  side_panel: { default_path: string };
  options_page: string;
};

const manifest = JSON.parse(readFileSync("public/manifest.json", "utf8")) as Manifest;
const viteConfig = readFileSync("vite.config.ts", "utf8");

function isSpecificHostPattern(pattern: string): boolean {
  if (pattern === "<all_urls>") {
    return false;
  }
  const match = /^(https?):\/\/([^/]+)\/.*$/.exec(pattern);
  if (match === null) {
    return false;
  }
  const [, scheme, host] = match;
  if (host.includes("*")) {
    return false;
  }
  const localhost = /^(localhost|127\.0\.0\.1)(:\d+)?$/.test(host);
  return localhost || scheme === "https";
}

describe("manifest", () => {
  it("is manifest v3", () => {
    expect(manifest.manifest_version).toBe(3);
  });

  it("names entry points the build actually produces", () => {
    expect(manifest.side_panel.default_path).toBe("sidepanel.html");
    expect(manifest.options_page).toBe("options.html");
    expect(manifest.background.service_worker).toBe("background.js");

    expect(viteConfig).toContain('sidepanel: "sidepanel.html"');
    expect(viteConfig).toContain('options: "options.html"');
    expect(viteConfig).toContain('background: "src/background/service-worker.ts"');
    expect(viteConfig).toContain('entryFileNames: "[name].js"');
  });

  it("runs the service worker as a module so it can import shared code", () => {
    expect(manifest.background.type).toBe("module");
  });

  it("injects the content script only on the demo pages and the chats it adapts", () => {
    expect(manifest.content_scripts).toHaveLength(2);

    const [demo, chat] = manifest.content_scripts;
    expect(demo.js).toEqual(["content.js"]);
    expect(chat.js).toEqual(["content.js"]);
    expect(viteConfig).toContain('content: "src/content/main.ts"');

    for (const match of demo.matches) {
      expect(isSpecificHostPattern(match)).toBe(true);
      expect(match.endsWith("/demo/*")).toBe(true);
    }

    const allowedChatHosts = [
      "web.whatsapp.com",
      "discord.com",
      "www.messenger.com",
      "www.facebook.com"
    ];
    for (const match of chat.matches) {
      expect(isSpecificHostPattern(match)).toBe(true);
      expect(match.startsWith("https://")).toBe(true);
      expect(allowedChatHosts).toContain(new URL(match.replace("/*", "/")).hostname);
    }
  });

  it("scopes the chat hosts to conversation paths, never a whole site", () => {
    const [, chat] = manifest.content_scripts;
    const wholeSite = chat.matches.filter((match) => {
      const path = new URL(match.replace("/*", "/")).pathname;
      return path === "/";
    });

    expect(wholeSite).toEqual(["https://web.whatsapp.com/*"]);
  });

  it("can read those chats but cannot make requests to them", () => {
    const chatHosts = manifest.content_scripts[1].matches
      .map((match) => new URL(match.replace("/*", "/")).hostname);

    for (const host of chatHosts) {
      for (const permission of manifest.host_permissions) {
        expect(permission).not.toContain(host);
      }
    }
  });

  it("asks for network access only to its own backend", () => {
    const hosts = manifest.host_permissions
      .map((permission) => new URL(permission.replace("/*", "/")).hostname);

    expect(hosts.every((host) => host === "localhost"
      || host === "127.0.0.1"
      || host.endsWith("sslip.io"))).toBe(true);
  });

  it("asks for only the permissions it uses", () => {
    expect(manifest.permissions).toEqual(["sidePanel", "storage", "activeTab", "alarms"]);
    expect(manifest.permissions).not.toContain("tabs");
    expect(manifest.permissions).not.toContain("cookies");
    expect(manifest.permissions).not.toContain("webRequest");
    expect(manifest.permissions).not.toContain("notifications");
  });

  it("polls for PayPal activity on an alarm rather than keeping a worker awake", () => {
    expect(manifest.permissions).toContain("alarms");
    expect(readFileSync("src/background/service-worker.ts", "utf8"))
      .toContain("chrome.alarms.create");
  });

  it("treats wildcards and plain http on a remote host as unacceptable", () => {
    for (const bad of [
      "<all_urls>",
      "https://*/*",
      "http://*/*",
      "https://*.example.com/*",
      "http://pylos.example.com/*",
      "ftp://pylos.example.com/*"
    ]) {
      expect(isSpecificHostPattern(bad)).toBe(false);
    }

    expect(isSpecificHostPattern("https://pylos.example.com/*")).toBe(true);
    expect(isSpecificHostPattern("http://localhost:8080/*")).toBe(true);
    expect(isSpecificHostPattern("http://127.0.0.1:8080/demo/*")).toBe(true);
  });

  it("never asks for blanket host access", () => {
    for (const host of manifest.host_permissions) {
      expect(isSpecificHostPattern(host)).toBe(true);
    }
  });

  it("requires a chrome new enough for the side panel api", () => {
    expect(Number((manifest as unknown as { minimum_chrome_version: string }).minimum_chrome_version))
      .toBeGreaterThanOrEqual(114);
  });
});
