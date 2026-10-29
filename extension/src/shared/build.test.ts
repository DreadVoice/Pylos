import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const DIST = "dist";

function built(file: string): string {
  const path = `${DIST}/${file}`;
  if (!existsSync(path)) {
    throw new Error(`${path} is missing. Run npm run build before npm test.`);
  }
  return readFileSync(path, "utf8");
}

describe("build output", () => {
  it("produces every file the manifest names", () => {
    for (const file of ["manifest.json", "sidepanel.html", "options.html", "background.js", "content.js"]) {
      expect(existsSync(`${DIST}/${file}`)).toBe(true);
    }
  });

  it("emits the content script as a classic script", () => {
    const content = built("content.js");

    expect(content).not.toMatch(/^\s*import[ {(*]/m);
    expect(content).not.toMatch(/^\s*export[ {(*]/m);
  });

  it("keeps the content script free of react", () => {
    expect(built("content.js")).not.toContain("createRoot");
    expect(built("content.js").length).toBeLessThan(20000);
  });

  it("ships no api token or secret in the bundle", () => {
    for (const file of ["background.js", "sidepanel.js", "options.js", "content.js"]) {
      expect(built(file)).not.toMatch(/sk-|AIza|EO[A-Za-z0-9_-]{60}/);
    }
  });
});
