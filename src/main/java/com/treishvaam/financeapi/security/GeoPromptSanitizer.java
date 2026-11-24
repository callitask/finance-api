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
 */
package com.treishvaam.financeapi.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GeoPromptSanitizer {

    private static final Logger logger = LoggerFactory.getLogger(GeoPromptSanitizer.class);

    // Prompt-override signatures (OWASP LLM01:2026) — log + redact matches to [REDACTED]
    private static final Pattern PROMPT_OVERRIDE_PATTERN =
            Pattern.compile(
                    "(?i)(ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions"
                            + "|disregard\\s+system\\s+prompt"
                            + "|you\\s+are\\s+now\\s+in\\s+developer\\s+mode"
                            + "|reveal\\s+your\\s+system\\s+prompt)");

    // GEO boundary/prompt tags to strip (editorial fields)
    private static final Pattern BOUNDARY_TAGS_PATTERN =
            Pattern.compile(
                    "(?i)</?\\s*(semantic-chunk|script|system|assistant|user|instruction)[^>]*>");

    // Markdown image exfiltration: ![alt](url)
    private static final Pattern MD_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*\\]\\([^)]+\\)");

    /**
     * Strips the supplementary Unicode Tag Block (U+E0000..U+E007F), BiDi override/embedding
     * controls, zero-width obfuscators, and invisible ASCII C0 controls (except \t and \n).
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
        // BiDi overrides & embeddings (LRM, RLM, LRE, RLE, PDF, LRO, RLO, LRI, RLI, FSI)
        if ((cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2066 && cp <= 0x2069)
                || cp == 0x200E
                || cp == 0x200F) {
            return true;
        }
        // Zero-width obfuscators (ZWSP, ZWNJ, ZWJ, BOM) + word joiner
        if ((cp >= 0x200B && cp <= 0x200D) || cp == 0xFEFF || cp == 0x2060) return true;
        // ASCII C0 controls except \t (0x09) and \n (0x0A)
        if (cp < 0x20 && cp != 0x09 && cp != 0x0A) return true;
        return false;
    }

    /**
     * Sanitizes an editorial metadata field (title, author, keywords, metaDescription): 1. Strip
     * invisible Unicode controls. 2. Strip GEO/prompt boundary tags (`semantic-chunk`, `script`,
     * `system`, `assistant`, `user`, `instruction`, `
     * <!--`, `-->
     * `, `&lt;|im_start|&gt;`, `&lt;|im_end|&gt;`). 3. Strip Markdown image exfiltration
     * (`![alt](url)`). 4. Scan for prompt-override signatures — if matched: log ERROR ("AEGIS LLM01
     * PROMPT-INJECTION ATTEMPT DETECTED") and redact matches to `[REDACTED]`. 5. Collapse
     * newlines/tabs/multiple spaces into single spaces, trim, cap at maxLength.
     */
    public String sanitizeEditorialField(String input, int maxLength) {
        if (input == null) return "";
        String s = stripInvisibleControls(input);

        // 2. Strip GEO/prompt boundary tags
        s = BOUNDARY_TAGS_PATTERN.matcher(s).replaceAll("");
        s = s.replaceAll("<!--", "");
        s = s.replaceAll("-->", "");
        s = s.replaceAll("<\\|im_start\\|>", "");
        s = s.replaceAll("<\\|im_end\\|>", "");

        // 3. Strip Markdown image exfiltration
        s = MD_IMAGE_PATTERN.matcher(s).replaceAll("");

        // 4. Prompt-override scan — log + redact
        Matcher m = PROMPT_OVERRIDE_PATTERN.matcher(s);
        if (m.find()) {
            logger.error("AEGIS LLM01 PROMPT-INJECTION ATTEMPT DETECTED");
            s = m.replaceAll("[REDACTED]");
        }

        // 5. Collapse whitespace, trim, cap at maxLength
        s = s.replaceAll("[\\s]+", " ").trim();
        if (s.length() > maxLength) {
            s = s.substring(0, maxLength).trim();
        }
        return s;
    }

    /**
     * Sanitizes admin-authored Markdown entity blocks (`llmsTxtBlock`, `aiFeedChunk`): strips
     * invisible Unicode controls and `<script>` tags while preserving legitimate admin-authored
     * Markdown and `<semantic-chunk>` wrappers.
     */
    public String sanitizeEntityBlock(String input) {
        if (input == null) return null;
        String s = stripInvisibleControls(input);
        s = s.replaceAll("(?i)<script\\b[\\s\\S]*?</script>", "");
        s = s.replaceAll("(?i)</?script[^>]*>", "");
        return s;
    }

    /** Strict slug validation for canonical paths (`/blog/{slug}`). */
    public boolean isValidSlug(String slug) {
        return slug != null && slug.matches("^[A-Za-z0-9_-]{1,120}$");
    }
}
