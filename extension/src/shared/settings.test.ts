import { describe, expect, it } from "vitest";
import {
  apiUrl,
  describeSettingsProblem,
  normaliseBackendUrl,
  type Settings
} from "./settings";

const good: Settings = { backendUrl: "http://localhost:8080", apiToken: "a-token" };

describe("normaliseBackendUrl", () => {
  it("trims whitespace and trailing slashes", () => {
    expect(normaliseBackendUrl("  http://localhost:8080/  ")).toBe("http://localhost:8080");
    expect(normaliseBackendUrl("http://localhost:8080///")).toBe("http://localhost:8080");
  });

  it("leaves a clean url alone", () => {
    expect(normaliseBackendUrl("https://pylos.example.com")).toBe("https://pylos.example.com");
  });
});

describe("apiUrl", () => {
  it("joins the base and path with exactly one slash", () => {
    expect(apiUrl(good, "/api/actions")).toBe("http://localhost:8080/api/actions");
    expect(apiUrl(good, "api/actions")).toBe("http://localhost:8080/api/actions");
    expect(apiUrl({ ...good, backendUrl: "http://localhost:8080/" }, "/api/assist")).toBe(
      "http://localhost:8080/api/assist"
    );
  });
});

describe("describeSettingsProblem", () => {
  it("accepts a complete configuration", () => {
    expect(describeSettingsProblem(good)).toBeNull();
    expect(describeSettingsProblem({ ...good, backendUrl: "https://pylos.example.com" })).toBeNull();
  });

  it("asks for a backend url when it is missing", () => {
    expect(describeSettingsProblem({ ...good, backendUrl: "   " })).toMatch(/backend URL/);
  });

  it("rejects a url it cannot parse", () => {
    expect(describeSettingsProblem({ ...good, backendUrl: "not a url" })).toMatch(/not a valid URL/);
  });

  it("rejects schemes a browser cannot call", () => {
    expect(describeSettingsProblem({ ...good, backendUrl: "ftp://localhost:8080" })).toMatch(
      /http:\/\/ or https:\/\//
    );
    expect(describeSettingsProblem({ ...good, backendUrl: "file:///etc/passwd" })).toMatch(
      /http:\/\/ or https:\/\//
    );
  });

  it("asks for a token when it is missing", () => {
    expect(describeSettingsProblem({ ...good, apiToken: "" })).toMatch(/API token/);
    expect(describeSettingsProblem({ ...good, apiToken: "   " })).toMatch(/API token/);
  });
});
