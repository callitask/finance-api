/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Bootstraps the AEGIS Expression Language system by parsing raw `.ael` text files
 * into ANTLR contexts.
 *
 * <p>Scope: - Converts String/File input into executing policies.
 *
 * <p>Critical Dependencies: - Core ANTLR4 Runtime (`CharStreams`, `CommonTokenStream`).
 *
 * <p>Security Constraints: - Syntax errors must trigger hard application startup failure to prevent
 * deploying broken security logic.
 *
 * <p>Non-Negotiables: - Do not swallow parse exceptions.
 *
 * <p>Change Intent: - Phase 5: Created Rule Loader to enable dynamic policy ingestion.
 *
 * <p>Future AI Guidance: - Can be expanded to read policies dynamically from Redis or a secure
 * Vault backend at runtime.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Implemented `AelRuleLoader`. • Why it was
 * added: Required to feed the generated ANTLR Lexers and Parsers safely. • Date: 2026-05-22
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis.ael;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AelRuleLoader {

    /**
     * Parses a raw AEL string into a verified PolicyFile context. Throws an exception if syntax is
     * malformed.
     */
    public aegisParser.PolicyFileContext loadRules(String aelContent) {
        try {
            aegisLexer lexer = new aegisLexer(CharStreams.fromString(aelContent));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            aegisParser parser = new aegisParser(tokens);

            // Set error strategy to fail hard on syntax errors
            parser.setErrorHandler(new org.antlr.v4.runtime.BailErrorStrategy());

            log.info("AEGIS AEL: Successfully parsed AEL policy ruleset.");
            return parser.policyFile();

        } catch (Exception e) {
            log.error(
                    "AEGIS AEL: CRITICAL FAILURE parsing security policies. Syntax error detected.",
                    e);
            throw new IllegalArgumentException("Invalid AEL syntax in policy definition", e);
        }
    }

    /** Extracts individual policies from the loaded file context. */
    public List<aegisParser.PolicyContext> extractPolicies(aegisParser.PolicyFileContext context) {
        return context.policy().stream().collect(Collectors.toList());
    }
}
