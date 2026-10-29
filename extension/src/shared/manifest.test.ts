import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

type Manifest = {
  manifest_version: number;
  name: string;
  version: string;
  permissions: string[];
  host_permissions: string[];
  background: { service_worker: string; type: string };
  side_panel: { default_path: string };
  options_page: string;
};

const manifest = JSON.parse(readFileSync("public/manifest.json", "utf8")) as Manifest;
const viteConfig = readFileSync("vite.config.ts", "utf8");

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

  it("asks for only the permissions it uses", () => {
    expect(manifest.permissions).toEqual(["sidePanel", "storage", "activeTab"]);
    expect(manifest.permissions).not.toContain("tabs");
    expect(manifest.permissions).not.toContain("cookies");
    expect(manifest.permissions).not.toContain("webRequest");
  });

  it("never asks for blanket host access", () => {
    expect(manifest.host_permissions).not.toContain("<all_urls>");
    expect(manifest.host_permissions).not.toContain("https://*/*");
    expect(manifest.host_permissions).not.toContain("http://*/*");
    for (const host of manifest.host_permissions) {
      expect(host).toMatch(/^https?:\/\/(localhost|127\.0\.0\.1):\d+\/\*$/);
    }
  });

  it("requires a chrome new enough for the side panel api", () => {
    expect(Number((manifest as unknown as { minimum_chrome_version: string }).minimum_chrome_version))
      .toBeGreaterThanOrEqual(114);
  });
});
