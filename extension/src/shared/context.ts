export type SiteType = "CHAT" | "EMAIL" | "PAYPAL_DASHBOARD" | "STORE_ADMIN" | "DEMO";

export type PageHints = {
  invoice_ids: string[];
  dispute_ids: string[];
};

export type PageContext = {
  site_type: SiteType;
  url: string;
  title: string;
  text: string;
  hints: PageHints;
};

export const MAX_EXTRACTED_CHARS = 20000;

export function emptyHints(): PageHints {
  return { invoice_ids: [], dispute_ids: [] };
}
