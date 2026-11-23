package com.treishvaam.financeapi.security;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Server-side Jsoup-based XSS sanitization for stored BlogPost HTML content (Stored
 * XSS closure, VULN-12).
 *
 * <p>Scope: - Provides a strict Jsoup Safelist (relaxed + editorial/media tags + protocol
 * enforcement) and post-processing (YouTube-only iframes, javascript:/vbscript:/data:text/html URL
 * stripping) for server-rendered and edge-materialized post HTML.
 *
 * <p>Critical Dependencies: - `org.jsoup:jsoup:1.18.3` (existing dependency, zero new deps).
 *
 * <p>Security Constraints: - `Safelist.preserveRelativeLinks(true)` + baseUri
 * `https://treishvaamfinance.com` (anti-regression: preserves `/api/v1/uploads/...` relative URLs).
 * - Only `http`/`https` protocols allowed on `video[src]`, `source[src]`, `iframe[src]`. - Iframes
 * limited to YouTube embed URLs.
 *
 * <p>Non-Negotiables: - Never trust raw `post.getContent()` when materializing to `#server-content`
 * or serializing `window.__PRELOADED_STATE__` — always route through `sanitizeHtml()` /
 * `escapeForScriptTag()`.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (P2.2 - Full-Stack Stored XSS Closure,
 * VULN-12): • Created HtmlSanitizationService — Safelist from `Safelist.relaxed()` +
 * `preserveRelativeLinks(true)` + Tiptap v3 tags (video, source, figure, figcaption, mark, span,
 * div, iframe) + class/id on :all + a[target,rel] + img/video/source/iframe attributes + http/https
 * protocol enforcement on video/source/iframe src. • Post-processing removes non-YouTube-embed
 * iframes and strips javascript:/vbscript:/data:text/html href/src. • `escapeForScriptTag()` for
 * safe JSON injection into `window.__PRELOADED_STATE__` and JSON-LD.
 *
 * <p>- EDITED (Batch 2B, Step 0): • Added Tiptap v3 strikethrough `s` and divider `hr` to the
 * safelist so Tiptap `<s>`/`<hr>` stored content is preserved and materialized. • Behavior
 * preserved: prior safelist tags/attributes/protocol rules unchanged; YouTube-only iframe
 * enforcement and dangerous-scheme stripping still applied.
 */
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

@Service
public class HtmlSanitizationService {

    private static final String YOUTUBE_EMBED_IFRAME_REGEX =
            "^https://(www\\.)?(youtube\\.com/embed/|youtube-nocookie\\.com/embed/)"
                    + "[A-Za-z0-9_-]+([?&#].*)?$";

    /**
     * Build the strict Jsoup Safelist for post content: `Safelist.relaxed()` + relative-link
     * preservation + Tiptap v3 editorial/media tags + class/id on all elements + safe media
     * attributes + http/https protocol enforcement.
     */
    public Safelist buildSafelist() {
        Safelist safelist = Safelist.relaxed();
        // Anti-regression guard: without preserveRelativeLinks(true) Jsoup strips every relative
        // img/video/iframe src — breaking /api/v1/uploads/... URLs.
        safelist.preserveRelativeLinks(true);
        safelist.addTags(
                "video",
                "source",
                "figure",
                "figcaption",
                "mark",
                "span",
                "div",
                "iframe",
                "s",
                "hr");
        safelist.addAttributes(":all", "class", "id");
        safelist.addAttributes("a", "target", "rel");
        safelist.addAttributes("img", "alt", "title", "width", "height", "loading");
        safelist.addAttributes(
                "video",
                "src",
                "controls",
                "poster",
                "playsinline",
                "preload",
                "muted",
                "loop",
                "width",
                "height");
        safelist.addAttributes("source", "src", "type");
        safelist.addAttributes(
                "iframe",
                "src",
                "width",
                "height",
                "allow",
                "allowfullscreen",
                "frameborder",
                "title");
        safelist.addProtocols("video", "src", "http", "https");
        safelist.addProtocols("source", "src", "http", "https");
        safelist.addProtocols("iframe", "src", "http", "https");
        return safelist;
    }

    /**
     * Sanitize raw HTML for server-rendered post content. Returns the cleaned body fragment with
     * non-YouTube iframes removed and dangerous href/src schemes stripped.
     */
    public String sanitizeHtml(String rawHtml) {
        if (rawHtml == null || rawHtml.isEmpty()) {
            return "";
        }
        String cleaned = Jsoup.clean(rawHtml, "https://treishvaamfinance.com", buildSafelist());
        Document doc = Jsoup.parseBodyFragment(cleaned);
        doc.outputSettings().prettyPrint(false);
        // Block arbitrary iframe phishing/XSS while preserving Tiptap
        // @tiptap/extension-youtube embed URLs.
        doc.select("iframe")
                .forEach(
                        iframe -> {
                            String src = iframe.attr("src");
                            if (src == null
                                    || src.isEmpty()
                                    || !src.matches(YOUTUBE_EMBED_IFRAME_REGEX)) {
                                iframe.remove();
                            }
                        });
        // Strip javascript:/vbscript:/data:text/html href/src on ANY element.
        doc.select("[href], [src]")
                .forEach(
                        el -> {
                            String href = el.attr("href").trim().toLowerCase(Locale.ROOT);
                            String src = el.attr("src").trim().toLowerCase(Locale.ROOT);
                            if (href.startsWith("javascript:")
                                    || href.startsWith("vbscript:")
                                    || href.startsWith("data:text/html")) {
                                el.removeAttr("href");
                            }
                            if (src.startsWith("javascript:")
                                    || src.startsWith("vbscript:")
                                    || src.startsWith("data:text/html")) {
                                el.removeAttr("src");
                            }
                        });
        return doc.body().html();
    }

    /**
     * Escape arbitrary JSON for safe injection into an inline {@code <script>} context: escapes
     * {@code <}, {@code >}, {@code &}, U+2028 and U+2029 (legacy JS line/paragraph separators).
     */
    public String escapeForScriptTag(String json) {
        if (json == null) return "null";
        return json.replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }
}
