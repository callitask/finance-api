/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - OWASP LLM01:2026 indirect prompt-injection firewall for GEO editorial content
 * (titles, authors, meta descriptions, keywords, slugs, entity blocks).
 *
 * <p>Scope: - Pure-Java, zero new deps. Strips Unicode Tag Block (U+E0000..U+E007F), BiDi
 * overrides/embeddings, zero-width obfuscators, invisible ASCII C0 controls, GEO/prompt boundary
 * tags, Markdown image exfiltration, and prompt-override signatures.
 *
 * <p>Security Constraints: - NEVER trust editorial strings when emitting `/ai-feed.md`,
 * `<semantic-chunk>` blocks, or `/llms.txt` entity blocks (VULN-13b). - Fail-safe: log-and-redact
 * on prompt-override signatures; strip everything dangerous.
 *
 * <p>Change Intent: - Created as part of Phase 3 Batch 4: "AI / GEO Indirect Prompt Injection
 * Defense" (P3.4).
 *
 * <p>Future AI Guidance: - Extend the signature scan list here (it lives in ONE place). Do not add
 * Markdown link exfiltration without regenerating the scan regexes consistently.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Batch 3B - P3.4, VULN-13b): • Created
 * GeoPromptSanitizer with `sanitizeEditorialField`, `sanitizeEntityBlock`,
 * `stripInvisibleControls`, and `isValidSlug`. - DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY
 * section acts as the institutional memory for future AI sessions. It must never be deleted,
 * truncated, rewritten, or regenerated. Future AI must append only.
 *
 * <p>- EDITED (2026-11-26 - Batch 2 §2.2 OWASP LLM01:2026 Firewall Hardening): • Expanded
 * `isInvisibleControl` (Variation Selectors, Soft Hyphen, CGJ, Invisible Math, Line/Para
 * Separators, DEL & C1 controls). • Added NFKC normalization, 1:1 Cyrillic/Greek homoglyph folding
 * (`foldHomoglyphs` + `redactPromptOverrides` preserving non-attack Greek/Unicode text), HTML
 * angle-bracket entity unescaping, Markdown inline/reference/autolink stripping, bounded 4-pass
 * nested tag & case-insensitive chat-delimiter removal, expanded `PROMPT_OVERRIDE_PATTERN`,
 * hardened `sanitizeEntityBlock` (16KB cap + multi-pass script/delimiter/override protection), and
 * recursive `sanitizeJsonLdNode(JsonNode)`.
 */
package com.treishvaam.financeapi.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GeoPromptSanitizer {

    private static final Logger logger = LoggerFactory.getLogger(GeoPromptSanitizer.class);
    private static final int MAX_ENTITY_BLOCK_LENGTH = 16_384;
    private static final int MAX_JSONLD_TEXT_LENGTH = 2000;
    private static final int MAX_JSONLD_DEPTH = 16;

    // Prompt-override signatures (OWASP LLM01:2026) — log + redact matches to [REDACTED]
    private static final Pattern PROMPT_OVERRIDE_PATTERN =
            Pattern.compile(
                    "(?i)(ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions"
                            + "|disregard\\s+system\\s+prompt"
                            + "|you\\s+are\\s+now\\s+in\\s+developer\\s+mode"
                            + "|reveal\\s+your\\s+system\\s+prompt"
                            + "|forget\\s+(all\\s+)?(previous|prior|above)\\s+instructions"
                            + "|new\\s+instructions\\s*:"
                            + "|override\\s+(system\\s+)?instructions"
                            + "|begin\\s+system\\s+prompt"
                            + "|###\\s*system\\b"
                            + "|act\\s+as\\s+(a\\s+)?different\\b"
                            + "|\\bjailbreak\\b"
                            + "|bypass\\s+(all\\s+)?safety\\b)");

    // GEO boundary/prompt tags to strip (editorial fields)
    private static final Pattern BOUNDARY_TAGS_PATTERN =
            Pattern.compile(
                    "(?i)</?\\s*(semantic-chunk|script|system|assistant|user|instruction)[^>]*>");

    // Unclosed dangerous start tags at end of string
    private static final Pattern UNCLOSED_TAG_PATTERN =
            Pattern.compile(
                    "(?i)<\\s*(?:semantic-chunk|script|system|assistant|user|instruction)\\b[^>]*$");

    private static final Pattern UNCLOSED_SCRIPT_TAG_PATTERN =
            Pattern.compile("(?i)<\\s*script\\b[^>]*$");

    // Case-insensitive chat & markup delimiters
    private static final Pattern CHAT_DELIMITERS_PATTERN =
            Pattern.compile(
                    "(?i)(?:<\\|(?:im_start|im_end|system|assistant|user)\\|>"
                            + "|\\[/?INST\\]"
                            + "|<<\\s*/?\\s*SYS\\s*>>"
                            + "|<!\\[CDATA\\["
                            + "|\\]\\]>"
                            + "|<!--"
                            + "|-->)");

    private static final Pattern LT_ENTITY_PATTERN = Pattern.compile("(?i)&lt;|&#0*60;|&#x0*3c;");
    private static final Pattern GT_ENTITY_PATTERN = Pattern.compile("(?i)&gt;|&#0*62;|&#x0*3e;");

    // Markdown image and link exfiltration patterns
    private static final Pattern MD_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*\\]\\([^)]+\\)");
    private static final Pattern MD_LINK_PATTERN = Pattern.compile("\\[([^\\]]*)\\]\\([^)]+\\)");
    private static final Pattern MD_REF_LINK_PATTERN =
            Pattern.compile("(?m)^\\s*\\[[^\\]]+\\]:\\s*\\S+.*$");
    private static final Pattern AUTOLINK_PATTERN = Pattern.compile("(?i)<https?://[^>]+>");

    // Script stripping patterns for entity blocks (preserving <semantic-chunk>)
    private static final Pattern SCRIPT_BLOCK_PATTERN =
            Pattern.compile("(?i)<script\\b[\\s\\S]*?</script>");
    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("(?i)</?script[^>]*>");
    private static final Pattern ENCODED_SCRIPT_TAG_PATTERN =
            Pattern.compile(
                    "(?i)(?:&lt;|&#0*60;|&#x0*3c;)\\s*/?\\s*script\\b[^>&]*(?:&gt;|&#0*62;|&#x0*3e;)?");

    private static final Set<String> JSONLD_STRUCTURAL_OR_URL_KEYS =
            Set.of(
                    "@id",
                    "@context",
                    "@type",
                    "url",
                    "sameAs",
                    "logo",
                    "image",
                    "mainEntityOfPage",
                    "embedUrl",
                    "contentUrl",
                    "thumbnailUrl");

    /**
     * Strips the supplementary Unicode Tag Block (U+E0000..U+E007F), Variation Selectors, BiDi
     * override/embedding controls, zero-width obfuscators, invisible math/formatting controls, and
     * invisible ASCII C0/DEL/C1 controls (except \t and \n).
     */
    public String stripInvisibleControls(String input) {
        if (input == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i = input.offsetByCodePoints(i, 1)) {
            int cp = input.codePointAt(i);
            if (!isInvisibleControl(cp)) {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    private boolean isInvisibleControl(int cp) {
        // Unicode Tag Block (supplementary, U+E0000..U+E007F)
        if (cp >= 0xE0000 && cp <= 0xE007F) return true;
        // Variation Selectors (emoji-smuggling prompt injection)
        if ((cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0xE0100 && cp <= 0xE01EF)) return true;
        // BiDi overrides & embeddings (LRM, RLM, LRE, RLE, PDF, LRO, RLO, LRI, RLI, FSI)
        if ((cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2066 && cp <= 0x2069)
                || cp == 0x200E
                || cp == 0x200F) {
            return true;
        }
        // Zero-width obfuscators (ZWSP, ZWNJ, ZWJ, BOM) + word joiner
        if ((cp >= 0x200B && cp <= 0x200D) || cp == 0xFEFF || cp == 0x2060) return true;
        // Soft Hyphen, Combining Grapheme Joiner, Invisible Math Operators, Line/Para Separators
        if (cp == 0x00AD
                || cp == 0x034F
                || (cp >= 0x2061 && cp <= 0x2064)
                || cp == 0x2028
                || cp == 0x2029) {
            return true;
        }
        // ASCII C0 controls except \t (0x09) and \n (0x0A), plus DEL and C1 controls
        if ((cp < 0x20 && cp != 0x09 && cp != 0x0A) || cp == 0x7F || (cp >= 0x80 && cp <= 0x9F)) {
            return true;
        }
        return false;
    }

    /**
     * 1:1 BMP homoglyph folder mapping common Cyrillic/Greek lookalike letters to ASCII equivalents
     * while guaranteeing {@code folded.length() == input.length()}.
     */
    private String foldHomoglyphs(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            char mapped =
                    switch (c) {
                            // Cyrillic lowercase
                        case '\u0430' -> 'a';
                        case '\u0432' -> 'b';
                        case '\u0435' -> 'e';
                        case '\u043A' -> 'k';
                        case '\u043C' -> 'm';
                        case '\u043D' -> 'h';
                        case '\u043E' -> 'o';
                        case '\u0440' -> 'p';
                        case '\u0441' -> 'c';
                        case '\u0442' -> 't';
                        case '\u0443' -> 'y';
                        case '\u0445' -> 'x';
                        case '\u0455' -> 's';
                        case '\u0456' -> 'i';
                        case '\u0458' -> 'j';
                            // Cyrillic uppercase
                        case '\u0410' -> 'A';
                        case '\u0412' -> 'B';
                        case '\u0415' -> 'E';
                        case '\u041A' -> 'K';
                        case '\u041C' -> 'M';
                        case '\u041D' -> 'H';
                        case '\u041E' -> 'O';
                        case '\u0420' -> 'P';
                        case '\u0421' -> 'C';
                        case '\u0422' -> 'T';
                        case '\u0423' -> 'Y';
                        case '\u0425' -> 'X';
                        case '\u0405' -> 'S';
                        case '\u0406' -> 'I';
                        case '\u0408' -> 'J';
                            // Greek uppercase
                        case '\u0391' -> 'A';
                        case '\u0392' -> 'B';
                        case '\u0395' -> 'E';
                        case '\u0396' -> 'Z';
                        case '\u0397' -> 'H';
                        case '\u0399' -> 'I';
                        case '\u039A' -> 'K';
                        case '\u039C' -> 'M';
                        case '\u039D' -> 'N';
                        case '\u039F' -> 'O';
                        case '\u03A1' -> 'P';
                        case '\u03A4' -> 'T';
                        case '\u03A5' -> 'Y';
                        case '\u03A7' -> 'X';
                            // Greek lowercase
                        case '\u03B1' -> 'a';
                        case '\u03B5' -> 'e';
                        case '\u03B9' -> 'i';
                        case '\u03BA' -> 'k';
                        case '\u03BD' -> 'v';
                        case '\u03BF' -> 'o';
                        case '\u03C1' -> 'p';
                        case '\u03C4' -> 't';
                        case '\u03C7' -> 'x';
                        default -> c;
                    };
            sb.append(mapped);
        }
        return sb.toString();
    }

    private String redactPromptOverrides(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String folded = foldHomoglyphs(input);
        Matcher m = PROMPT_OVERRIDE_PATTERN.matcher(folded);
        if (!m.find()) {
            return input;
        }
        logger.error("AEGIS LLM01 PROMPT-INJECTION ATTEMPT DETECTED");
        StringBuilder out = new StringBuilder(input.length());
        int lastEnd = 0;
        do {
            out.append(input, lastEnd, m.start()).append("[REDACTED]");
            lastEnd = m.end();
        } while (m.find());
        out.append(input, lastEnd, input.length());
        return out.toString();
    }

    private String unescapeAngleBrackets(String input) {
        String s = LT_ENTITY_PATTERN.matcher(input).replaceAll("<");
        return GT_ENTITY_PATTERN.matcher(s).replaceAll(">");
    }

    /**
     * Sanitizes an editorial metadata field (title, author, keywords, metaDescription): 1. Strip
     * invisible Unicode controls. 2. Apply Unicode NFKC normalization. 3. Unescape HTML
     * angle-bracket entities. 4. Strip Markdown image/link/reference/autolink exfiltration (images
     * before links). 5. Bounded multi-pass strip of GEO/prompt boundary tags, unclosed tags, and
     * chat delimiters. 6. Homoglyph-aware prompt-override scan (log ERROR + redact to
     * `[REDACTED]`). 7. Collapse whitespace, trim, cap at maxLength.
     */
    public String sanitizeEditorialField(String input, int maxLength) {
        if (input == null || maxLength <= 0) return "";
        String s = stripInvisibleControls(input);
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        s = unescapeAngleBrackets(s);

        // Strip Markdown exfiltration (images BEFORE inline links)
        s = MD_IMAGE_PATTERN.matcher(s).replaceAll("");
        s = MD_LINK_PATTERN.matcher(s).replaceAll("$1");
        s = MD_REF_LINK_PATTERN.matcher(s).replaceAll("");
        s = AUTOLINK_PATTERN.matcher(s).replaceAll("");

        // Bounded multi-pass boundary tag & chat delimiter strip
        for (int pass = 0; pass < 4; pass++) {
            String next = BOUNDARY_TAGS_PATTERN.matcher(s).replaceAll("");
            next = UNCLOSED_TAG_PATTERN.matcher(next).replaceAll("");
            next = CHAT_DELIMITERS_PATTERN.matcher(next).replaceAll("");
            if (next.equals(s)) {
                break;
            }
            s = next;
        }

        // Homoglyph-aware prompt-override scan — log + redact
        s = redactPromptOverrides(s);

        // Collapse whitespace, trim, cap at maxLength
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > maxLength) {
            s = s.substring(0, maxLength).trim();
        }
        return s;
    }

    /**
     * Sanitizes admin-authored Markdown entity blocks (`llmsTxtBlock`, `aiFeedChunk`): strips
     * invisible Unicode controls, normalizes NFKC, removes nested/encoded `<script>` tags and chat
     * delimiters across bounded passes, redacts prompt overrides, and caps length at 16,384 chars
     * while preserving legitimate admin-authored Markdown and `<semantic-chunk>` wrappers.
     */
    public String sanitizeEntityBlock(String input) {
        if (input == null) return null;
        String s = stripInvisibleControls(input);
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        for (int pass = 0; pass < 4; pass++) {
            String next = SCRIPT_BLOCK_PATTERN.matcher(s).replaceAll("");
            next = SCRIPT_TAG_PATTERN.matcher(next).replaceAll("");
            next = UNCLOSED_SCRIPT_TAG_PATTERN.matcher(next).replaceAll("");
            next = ENCODED_SCRIPT_TAG_PATTERN.matcher(next).replaceAll("");
            next = CHAT_DELIMITERS_PATTERN.matcher(next).replaceAll("");
            if (next.equals(s)) {
                break;
            }
            s = next;
        }
        s = redactPromptOverrides(s);
        if (s.length() > MAX_ENTITY_BLOCK_LENGTH) {
            s = s.substring(0, MAX_ENTITY_BLOCK_LENGTH);
        }
        return s;
    }

    /**
     * Recursively sanitizes JSON-LD {@link ObjectNode}/{@link ArrayNode} text values: preserves
     * structural/URL keys (`@id`, `@context`, `@type`, `url`, `sameAs`, `logo`, `image`, etc.) with
     * invisible-control stripping only while sanitizing descriptive text fields via {@link
     * #sanitizeEditorialField(String, int)}.
     */
    public void sanitizeJsonLdNode(JsonNode node) {
        sanitizeJsonLdRecursive(node, false, 0);
    }

    private void sanitizeJsonLdRecursive(
            JsonNode node, boolean isStructuralOrUrlContext, int depth) {
        if (node == null || depth > MAX_JSONLD_DEPTH) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> fieldNames = new ArrayList<>();
            obj.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                JsonNode child = obj.get(fieldName);
                if (child == null) {
                    continue;
                }
                boolean keyIsUrlOrStructural =
                        isStructuralOrUrlContext
                                || JSONLD_STRUCTURAL_OR_URL_KEYS.contains(fieldName);
                if (child.isTextual()) {
                    String cleaned =
                            keyIsUrlOrStructural
                                    ? stripInvisibleControls(child.asText())
                                    : sanitizeEditorialField(
                                            child.asText(), MAX_JSONLD_TEXT_LENGTH);
                    obj.set(fieldName, TextNode.valueOf(cleaned));
                } else {
                    sanitizeJsonLdRecursive(child, keyIsUrlOrStructural, depth + 1);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode item = arr.get(i);
                if (item == null) {
                    continue;
                }
                if (item.isTextual()) {
                    String cleaned =
                            isStructuralOrUrlContext
                                    ? stripInvisibleControls(item.asText())
                                    : sanitizeEditorialField(item.asText(), MAX_JSONLD_TEXT_LENGTH);
                    arr.set(i, TextNode.valueOf(cleaned));
                } else {
                    sanitizeJsonLdRecursive(item, isStructuralOrUrlContext, depth + 1);
                }
            }
        }
    }

    /** Strict slug validation for canonical paths (`/blog/{slug}`). */
    public boolean isValidSlug(String slug) {
        return slug != null && slug.matches("^[A-Za-z0-9_-]{1,120}$");
    }
}
