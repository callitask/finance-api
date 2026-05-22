/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - ANTLR4 Grammar definition for the AEGIS Expression Language (AEL).
 * - A domain-specific language preventing code-injection while allowing dynamic security rules.
 *
 * Scope:
 * - Strictly defines the syntactic structure of AEGIS behavior, network, crypto, and action policies.
 *
 * Critical Dependencies:
 * - Requires `antlr4-maven-plugin` to generate Java implementations at build time.
 *
 * Security Constraints:
 * - Must NOT support looping, arbitrary execution, system imports, or reflection logic.
 *
 * Non-Negotiables:
 * - Every policy must resolve deterministically to an Action (ALLOW, BLOCK, DECEPTION, etc).
 *
 * Change Intent:
 * - Phase 5: Created formal grammar to support verifiable dynamic defense-in-depth rules.
 * - FIX: Removed explicit @header block to prevent duplicate package declarations by the Maven plugin.
 *
 * Future AI Guidance:
 * - If new metrics or defensive actions are created (e.g. Rate Limiting variables), add them to the parser tokens here.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED:
 * • Implemented AEL formal grammar.
 * • Why it was added: Provides a human-readable, machine-verifiable way to orchestrate defensive layers without recompiling the backend.
 * • Date: 2026-05-22
 *
 * - EDITED:
 * • Removed trailing '}' at EOF.
 * • Removed explicit `@header` block.
 * • Why: It corrupted the ANTLR-generated Java artifacts by injecting double package declarations, throwing "class, interface, enum, or record expected" during `mvn compile`.
 *
 * - DO-NOT-DELETE RULE:
 * This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated, rewritten, or regenerated.
 * Future AI must append only.
 */

grammar aegis;

policyFile : policy* EOF;
policy : 'POLICY' ID '{' condition 'THEN' action ('ELSE' action)? '}' ;
condition
    : expr
    | condition 'AND' condition
    | condition 'OR' condition
    | 'NOT' condition
    | '(' condition ')'
    ;
expr
    : behavioralExpr
    | cryptoExpr  
    | networkExpr
    | timeExpr
    | scoreExpr
    ;
behavioralExpr
    : 'BEHAVIOR.' ('BOT_SCORE' | 'ENTROPY' | 'REQUEST_RATE' | 'ENDPOINT_COVERAGE') comparator NUMBER
    ;
cryptoExpr
    : 'CRYPTO.' ('JWT_VALID' | 'ZKP_VERIFIED' | 'PQC_SIGNED' | 'TOKEN_AGE') (comparator NUMBER)?
    ;
networkExpr
    : 'NETWORK.' ('JA3_HASH' | 'IP_REPUTATION' | 'GEO_COUNTRY' | 'ASN_TYPE') (comparator (STRING | NUMBER))?
    ;
timeExpr: 'TIME' comparator NUMBER ;
scoreExpr: 'SCORE' comparator NUMBER ;

action
    : 'ALLOW'
    | 'BLOCK' ('(' STRING ')')?
    | 'DECEPTION'
    | 'TARPIT' '(' NUMBER 'ms' ')'
    | 'ROTATE_MANIFEST'
    | 'EMERGENCY'
    | 'LOG' '(' STRING ')'
    | 'CHALLENGE' ('ZKP' | 'POW' | 'CAPTCHA')
    ;

comparator : '>' | '<' | '>=' | '<=' | '==' | '!=' ;

ID : [a-zA-Z_][a-zA-Z0-9_]* ;
NUMBER : [0-9]+ ('.' [0-9]+)? ;
STRING : '"' .*? '"' ;
WS : [ \t\r\n]+ -> skip ;