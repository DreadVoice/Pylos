import {
  MAX_EXTRACTED_CHARS,
  emptyHints,
  type PageContext,
  type PageHints,
  type SiteType
} from "../shared/context";
import {
  chatHints,
  formatChat,
  readDiscord,
  readMessenger,
  readWhatsApp,
  type ChatReader
} from "./chat";
import { collapse, demoSiteType, matchesDemo, readDemoHints, readDemoPage } from "./demo";

export type Adapter = {
  name: string;
  siteType: SiteType;
  matches: (url: URL) => boolean;
  extract: (document: Document) => string;
  hints?: (document: Document) => PageHints;
};

function chatAdapter(name: string, matches: (url: URL) => boolean, read: ChatReader): Adapter {
  return {
    name,
    siteType: "CHAT",
    matches,
    extract: (document) => formatChat(read(document)),
    hints: (document) => chatHints(read(document))
  };
}

function hostIs(url: URL, host: string): boolean {
  return url.hostname === host || url.hostname === `www.${host}`;
}

export const adapters: Adapter[] = [
  {
    name: "demo",
    siteType: demoSiteType,
    matches: matchesDemo,
    extract: readDemoPage,
    hints: readDemoHints
  },
  chatAdapter("whatsapp", (url) => hostIs(url, "web.whatsapp.com"), readWhatsApp),
  chatAdapter(
    "discord",
    (url) => hostIs(url, "discord.com") && url.pathname.startsWith("/channels/"),
    readDiscord
  ),
  chatAdapter(
    "messenger",
    (url) =>
      (hostIs(url, "messenger.com") && url.pathname.startsWith("/t/"))
      || (hostIs(url, "facebook.com") && url.pathname.startsWith("/messages/")),
    readMessenger
  )
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
