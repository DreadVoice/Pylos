import { adapterFor, buildPageContext } from "./router";

chrome.runtime.onMessage.addListener((message: unknown, _sender, sendResponse) => {
  if (typeof message !== "object" || message === null) {
    return false;
  }
  if ((message as { kind?: unknown }).kind !== "pylos.extract") {
    return false;
  }

  const adapter = adapterFor(new URL(window.location.href));
  if (adapter === null) {
    sendResponse({ supported: false, reason: "Pylos does not read this page." });
    return false;
  }

  const context = buildPageContext(adapter, document, window.location.href);
  if (context.text === "") {
    sendResponse({ supported: false, reason: "Nothing readable was found on this page yet." });
    return false;
  }

  sendResponse({ supported: true, context });
  return false;
});
