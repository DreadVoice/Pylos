const PLAIN_DECIMAL = /^-?[0-9]{1,15}([.][0-9]{1,2})?$/;

export function toHundredths(value: string | undefined): number | null {
  if (value === undefined) {
    return null;
  }
  const trimmed = value.trim();
  if (!PLAIN_DECIMAL.test(trimmed)) {
    return null;
  }

  const negative = trimmed.startsWith("-");
  const [whole, fraction = ""] = trimmed.replace("-", "").split(".");
  const hundredths = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  return negative ? -hundredths : hundredths;
}

export function fromHundredths(hundredths: number): string {
  const negative = hundredths < 0;
  const absolute = Math.abs(hundredths);
  const whole = Math.floor(absolute / 100);
  const fraction = String(absolute % 100).padStart(2, "0");
  return `${negative ? "-" : ""}${whole}.${fraction}`;
}

export function lineTotal(quantity: string | undefined, unitAmount: string | undefined): number | null {
  const quantityHundredths = toHundredths(quantity);
  const unitHundredths = toHundredths(unitAmount);
  if (quantityHundredths === null || unitHundredths === null) {
    return null;
  }
  return Math.round((quantityHundredths * unitHundredths) / 100);
}

export function invoiceTotal(
  items: { quantity?: string; unit_amount?: string }[] | undefined
): number | null {
  if (items === undefined || items.length === 0) {
    return null;
  }

  let total = 0;
  for (const item of items) {
    const line = lineTotal(item.quantity, item.unit_amount);
    if (line === null) {
      return null;
    }
    total += line;
  }
  return total;
}

export function describeTotal(
  currencyCode: string | undefined,
  items: { quantity?: string; unit_amount?: string }[] | undefined
): string {
  const total = invoiceTotal(items);
  if (total === null) {
    return "Total unavailable until every amount is a plain decimal";
  }
  return `${(currencyCode ?? "").toUpperCase()} ${fromHundredths(total)}`.trim();
}
