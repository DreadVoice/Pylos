package com.dreadvoice.pylos.context;

import com.dreadvoice.pylos.paypal.PayPalIdentifiers;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class ContextSanitizer {

    public static final String BEGIN_MARKER = "<<<PAGE_CONTENT_BEGIN>>>";
    public static final String END_MARKER = "<<<PAGE_CONTENT_END>>>";

    static final int MAX_TEXT_LENGTH = 8000;
    static final int MAX_URL_LENGTH = 500;
    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_HINTS = 20;
    static final String TRUNCATION_SUFFIX = " [truncated]";

    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile(
            "<(script|style)\\b[^>]*>.*?</\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UNCLOSED_SCRIPT_OR_STYLE = Pattern.compile(
            "<(script|style)\\b[^>]*>.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern HTML_TAG = Pattern.compile(
            "</?[A-Za-z][A-Za-z0-9-]*(\\s[^<>]*)?/?>");
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\n\\t]]");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[ \\t\\x0B\\f\\r]{2,}");
    private static final Pattern MARKER_TOKEN = Pattern.compile(
            "<{2,}\\s*/?\\s*PAGE_CONTENT[A-Z_]*\\s*>{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKER_WORD = Pattern.compile("PAGE_CONTENT_(BEGIN|END)",
            Pattern.CASE_INSENSITIVE);

    public SanitizedPageContext sanitize(PageContext pageContext) {
        String cleanedText = stripMarkup(pageContext.text());
        boolean truncated = cleanedText.length() > MAX_TEXT_LENGTH;
        if (truncated) {
            cleanedText = cleanedText.substring(0, MAX_TEXT_LENGTH) + TRUNCATION_SUFFIX;
        }

        return new SanitizedPageContext(
                pageContext.siteType(),
                singleLine(pageContext.url(), MAX_URL_LENGTH),
                singleLine(pageContext.title(), MAX_TITLE_LENGTH),
                cleanedText,
                filterHints(pageContext.hints()),
                truncated);
    }

    public String toDelimitedBlock(SanitizedPageContext context) {
        return BEGIN_MARKER + "\n"
                + "site_type: " + context.siteType() + "\n"
                + "url: " + nullToEmpty(context.url()) + "\n"
                + "title: " + nullToEmpty(context.title()) + "\n"
                + "visible_invoice_ids: " + String.join(", ", context.hints().invoiceIds()) + "\n"
                + "visible_dispute_ids: " + String.join(", ", context.hints().disputeIds()) + "\n"
                + "text:\n"
                + nullToEmpty(context.text()) + "\n"
                + END_MARKER;
    }

    String stripMarkup(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String working = SCRIPT_OR_STYLE.matcher(raw).replaceAll(" ");
        working = UNCLOSED_SCRIPT_OR_STYLE.matcher(working).replaceAll(" ");
        working = HTML_COMMENT.matcher(working).replaceAll(" ");
        working = unescapeEntities(working);
        working = HTML_TAG.matcher(working).replaceAll(" ");
        working = INVISIBLE.matcher(working).replaceAll("");
        working = MARKER_TOKEN.matcher(working).replaceAll(" ");
        working = MARKER_WORD.matcher(working).replaceAll(" ");
        working = HORIZONTAL_SPACE.matcher(working).replaceAll(" ");
        working = BLANK_LINES.matcher(working).replaceAll("\n\n");
        return working.strip();
    }

    private String unescapeEntities(String value) {
        return value
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private PageHints filterHints(PageHints hints) {
        if (hints == null) {
            return PageHints.empty();
        }
        List<String> invoiceIds = hints.invoiceIds().stream()
                .map(this::trimToNull)
                .filter(PayPalIdentifiers::isInvoiceId)
                .distinct()
                .limit(MAX_HINTS)
                .toList();
        List<String> disputeIds = hints.disputeIds().stream()
                .map(this::trimToNull)
                .filter(PayPalIdentifiers::isDisputeId)
                .distinct()
                .limit(MAX_HINTS)
                .toList();
        return new PageHints(invoiceIds, disputeIds);
    }

    private String singleLine(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String working = INVISIBLE.matcher(value).replaceAll("");
        working = working.replace("\n", " ").replace("\r", " ").replace("\t", " ");
        working = MARKER_TOKEN.matcher(working).replaceAll(" ");
        working = MARKER_WORD.matcher(working).replaceAll(" ");
        working = HORIZONTAL_SPACE.matcher(working).replaceAll(" ").strip();
        if (working.length() > maxLength) {
            return working.substring(0, maxLength);
        }
        return working;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
