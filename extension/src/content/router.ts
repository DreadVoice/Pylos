import {
  MAX_EXTRACTED_CHARS,
  emptyHints,
  type PageContext,
  type PageHints,
  type SiteType
} from "../shared/context";
import { collapse, demoSiteType, matchesDemo, readThread } from "./demo";

export type Adapter = {
  name: string;
  siteType: SiteType;
  matches: (url: URL) => boolean;
  extract: (document: Document) => string;
  hints?: (document: Document) => PageHints;
};

export const adapters: Adapter[] = [
  {
    name: "demo",
    siteType: demoSiteType,
    matches: matchesDemo,
    extract: readThread
  }
];

export function adapterFor(url: URL): Adapter | null {
  return adapters.find((adapter) => adapter.matches(url)) ?? null;
}

export function buildPageContext(adapter: Adapter, document: Document, href: string): PageContext {
  return {
    site_type: adapter.siteType,
    url: href,
    title: collapse(document.title),
    text: adapter.extract(document).slice(0, MAX_EXTRACTED_CHARS),
    hints: adapter.hints?.(document) ?? emptyHints()
  };
}
