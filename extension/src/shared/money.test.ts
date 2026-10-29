import { describe, expect, it } from "vitest";
import { describeTotal, fromHundredths, invoiceTotal, lineTotal, toHundredths } from "./money";

describe("toHundredths", () => {
  it("reads plain decimals exactly", () => {
    expect(toHundredths("25.00")).toBe(2500);
    expect(toHundredths("0.10")).toBe(10);
    expect(toHundredths("250.5")).toBe(25050);
    expect(toHundredths("7")).toBe(700);
    expect(toHundredths("-5.00")).toBe(-500);
    expect(toHundredths("  25.00  ")).toBe(2500);
  });

  it("refuses anything the backend would also refuse", () => {
    for (const bad of ["1e3", "250.000", "250,00", "+25.00", ".50", "25.", "abc", "$25", ""]) {
      expect(toHundredths(bad)).toBeNull();
    }
    expect(toHundredths(undefined)).toBeNull();
  });
});

describe("fromHundredths", () => {
  it("always writes two decimal places", () => {
    expect(fromHundredths(2500)).toBe("25.00");
    expect(fromHundredths(5)).toBe("0.05");
    expect(fromHundredths(0)).toBe("0.00");
    expect(fromHundredths(-500)).toBe("-5.00");
  });
});

describe("lineTotal", () => {
  it("multiplies without binary floating point error", () => {
    expect(lineTotal("2", "25.00")).toBe(5000);
    expect(lineTotal("3", "0.10")).toBe(30);
    expect(lineTotal("1", "0.20")).toBe(20);
  });

  it("is null when either side is not a plain decimal", () => {
    expect(lineTotal("1e3", "25.00")).toBeNull();
    expect(lineTotal("2", "25.000")).toBeNull();
  });
});

describe("invoiceTotal", () => {
  it("sums the lines the demo conversation produces", () => {
    expect(invoiceTotal([{ quantity: "2", unit_amount: "25.00" }])).toBe(5000);
  });

  it("sums fractions that a double would get wrong", () => {
    const total = invoiceTotal([
      { quantity: "3", unit_amount: "0.10" },
      { quantity: "1", unit_amount: "0.20" }
    ]);

    expect(total).toBe(50);
    expect(fromHundredths(total!)).toBe("0.50");
  });

  it("is null for no items or any bad line", () => {
    expect(invoiceTotal([])).toBeNull();
    expect(invoiceTotal(undefined)).toBeNull();
    expect(invoiceTotal([{ quantity: "2", unit_amount: "oops" }])).toBeNull();
  });
});

describe("describeTotal", () => {
  it("renders the currency and total", () => {
    expect(describeTotal("usd", [{ quantity: "2", unit_amount: "25.00" }])).toBe("USD 50.00");
  });

  it("says so rather than showing a wrong number", () => {
    expect(describeTotal("USD", [{ quantity: "2", unit_amount: "1e3" }])).toMatch(/unavailable/);
  });
});
