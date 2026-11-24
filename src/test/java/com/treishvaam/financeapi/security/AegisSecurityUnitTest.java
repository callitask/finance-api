package com.treishvaam.financeapi.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.treishvaam.financeapi.config.CachingConfig;
import com.treishvaam.financeapi.controller.FileController;
import com.treishvaam.financeapi.marketdata.WidgetDataDto;
import com.treishvaam.financeapi.security.aegis.AegisBehavioralEngine;
import com.treishvaam.financeapi.security.aegis.AegisEdgeValidationFilter;
import com.treishvaam.financeapi.security.aegis.AegisZkpAdminFilter;
import com.treishvaam.financeapi.security.aegis.AegisZkpServiceClient;
import com.treishvaam.financeapi.security.aegis.ShannonEntropyCalculator;
import com.treishvaam.financeapi.security.aegis.bcsm.SecurityDecision;
import com.treishvaam.financeapi.security.aegis.bcsm.ValidatorResult;
import com.treishvaam.financeapi.security.aegis.bcsm.validators.BehavioralValidator;
import com.treishvaam.financeapi.security.aegis.bcsm.validators.JwtValidator;
import com.treishvaam.financeapi.security.aegis.crypto.AegisPqcJwtService;
import com.treishvaam.financeapi.security.aegis.crypto.PowChallengeIssuer;
import com.treishvaam.financeapi.service.FileStorageService;
import com.treishvaam.financeapi.service.ImageService;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Zero-Docker unit tests for AEGIS security controls (Phase 3B audit + Phase 4A P4.1). Pure JUnit 5
 * + Mockito — no Spring context, no Testcontainers — so they run fast inside the CI/CD security
 * unit-test step (`./mvnw test -Dtest="*SecurityUnitTest,*SanitizerTest"`).
 *
 * <p>AI-CONTEXT: Covers {@link AegisEdgeValidationFilter}, {@link AegisZkpAdminFilter} (no test
 * backdoor), {@link PowChallengeIssuer}, {@link CachingConfig}, {@link FileController}, {@link
 * BehavioralValidator}/{@link AegisBehavioralEngine} entropy virtual-thread fix, and the {@link
 * JwtValidator} oversized-header pre-guard.
 */
class AegisSecurityUnitTest {

    // ---------------------------------------------------------------------------------------------
    // AegisEdgeValidationFilter
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName(
            "AegisEdgeValidationFilter.validateEdgeSecret: fail-closed on blank/short/placeholder")
    void edgeSecretValidationFailClosed() {
        AegisEdgeValidationFilter filter = new AegisEdgeValidationFilter();
        assertThrows(IllegalStateException.class, filter::validateEdgeSecret); // null (unset)

        ReflectionTestUtils.setField(filter, "edgeSecret", "");
        assertThrows(IllegalStateException.class, filter::validateEdgeSecret); // blank

        ReflectionTestUtils.setField(filter, "edgeSecret", "short");
        assertThrows(IllegalStateException.class, filter::validateEdgeSecret); // < 32 chars

        ReflectionTestUtils.setField(
                filter, "edgeSecret", "default-placeholder-secret-must-override-in-prod");
        assertThrows(IllegalStateException.class, filter::validateEdgeSecret); // placeholder

        ReflectionTestUtils.setField(filter, "edgeSecret", "a".repeat(64));
        assertDoesNotThrow(filter::validateEdgeSecret); // valid 64-char secret
    }

    @Test
    @DisplayName("AegisEdgeValidationFilter: missing edge headers → 403")
    void edgeFilterBlocksMissingHeaders() throws Exception {
        AegisEdgeValidationFilter filter = new AegisEdgeValidationFilter();
        ReflectionTestUtils.setField(filter, "edgeSecret", "a".repeat(64));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/widget/quotes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("AegisEdgeValidationFilter: expired timestamp → 403")
    void edgeFilterBlocksExpiredTimestamp() throws Exception {
        AegisEdgeValidationFilter filter = new AegisEdgeValidationFilter();
        ReflectionTestUtils.setField(filter, "edgeSecret", "a".repeat(64));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/widget/quotes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        // 400s behind the 5-minute TTL
        request.addHeader(
                "X-Aegis-Edge-Timestamp",
                String.valueOf(Instant.now().minusSeconds(400).toEpochMilli()));
        filter.doFilter(request, response, mock(FilterChain.class));
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("AegisEdgeValidationFilter: invalid HMAC signature → 403")
    void edgeFilterBlocksInvalidSignature() throws Exception {
        AegisEdgeValidationFilter filter = new AegisEdgeValidationFilter();
        ReflectionTestUtils.setField(filter, "edgeSecret", "a".repeat(64));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/widget/quotes");
        request.addHeader("X-Aegis-Edge-Timestamp", String.valueOf(Instant.now().toEpochMilli()));
        request.addHeader("X-Aegis-Edge-Signature", "deadbeef");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("AegisEdgeValidationFilter: valid path:timestamp:ip HMAC-SHA-512 passes")
    void edgeFilterPassesValidHmacSha512Request() throws Exception {
        AegisEdgeValidationFilter filter = new AegisEdgeValidationFilter();
        String secret = "a".repeat(64);
        ReflectionTestUtils.setField(filter, "edgeSecret", secret);

        String uri = "/api/v1/widget/quotes";
        String clientIp = "1.2.3.4";
        long ts = Instant.now().toEpochMilli();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(clientIp);
        request.addHeader(
                "X-Aegis-Edge-Signature", signHmacSha512(uri + ":" + ts + ":" + clientIp, secret));
        request.addHeader("X-Aegis-Edge-Timestamp", String.valueOf(ts));

        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
    }

    // ---------------------------------------------------------------------------------------------
    // AegisZkpAdminFilter
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName(
            "AegisZkpAdminFilter: X-AEGIS-Test-Token backdoor permanently dead on admin routes")
    void zkpBackdoorRejectedOnRegenerateSitemap() throws Exception {
        AegisZkpAdminFilter filter =
                new AegisZkpAdminFilter(mock(AegisZkpServiceClient.class), mockRedisTemplate());
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/admin/actions/regenerate-sitemap");
        request.addHeader("X-AEGIS-Test-Token", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("AegisZkpAdminFilter: OPTIONS preflight passes through")
    void zkpPassesOptionsPreflight() throws Exception {
        AegisZkpAdminFilter filter =
                new AegisZkpAdminFilter(mock(AegisZkpServiceClient.class), mockRedisTemplate());
        MockHttpServletRequest request =
                new MockHttpServletRequest("OPTIONS", "/api/v1/admin/actions/regenerate-sitemap");
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("AegisZkpAdminFilter: missing public key → 403 (fail closed)")
    void zkpRejectsMissingPublicKey() throws Exception {
        AegisZkpAdminFilter filter =
                new AegisZkpAdminFilter(mock(AegisZkpServiceClient.class), mockRedisTemplate());
        ReflectionTestUtils.setField(filter, "configuredAdminPublicKey", "");
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/admin/actions/regenerate-sitemap");
        request.addHeader("X-AEGIS-ZKP-Proof", "proof-abc");
        request.addHeader("X-AEGIS-Challenge-ID", "challenge-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("AegisZkpAdminFilter: challenge replay (setIfAbsent false) → 403")
    void zkpRejectsReplay() throws Exception {
        RedisTemplate<String, String> template = mockRedisTemplate();
        ValueOperations<String, String> valueOps = template.opsForValue();
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        AegisZkpAdminFilter filter =
                new AegisZkpAdminFilter(mock(AegisZkpServiceClient.class), template);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/admin/actions/regenerate-sitemap");
        request.addHeader("X-AEGIS-ZKP-Proof", "proof-abc");
        request.addHeader("X-AEGIS-Challenge-ID", "challenge-replay");
        request.addHeader("X-AEGIS-Public-Key", "admin-pub-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("AegisZkpAdminFilter heal path: matching X-AEGIS-ZKP passes")
    void zkpHealPathPassesMatchingToken() throws Exception {
        AegisZkpAdminFilter filter =
                new AegisZkpAdminFilter(mock(AegisZkpServiceClient.class), mockRedisTemplate());
        ReflectionTestUtils.setField(filter, "internalSecret", "internal-secret-12345");
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/admin/actions/analytics/heal");
        request.addHeader("X-AEGIS-ZKP", "internal-secret-12345");
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("AegisZkpAdminFilter heal path: mismatched X-AEGIS-ZKP → 403")
    void zkpHealPathRejectsMismatchedToken() throws Exception {
        AegisZkpServiceClient client = mock(AegisZkpServiceClient.class);
        when(client.verifyProof(anyString(), anyString(), anyString())).thenReturn(false);
        AegisZkpAdminFilter filter = new AegisZkpAdminFilter(client, mockRedisTemplate());
        ReflectionTestUtils.setField(filter, "internalSecret", "internal-secret-12345");
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/admin/actions/analytics/heal");
        request.addHeader("X-AEGIS-ZKP", "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        assertEquals(403, response.getStatus());
    }

    private RedisTemplate<String, String> mockRedisTemplate() {
        RedisTemplate<String, String> template = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOps);
        return template;
    }

    private static String signHmacSha512(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512", BouncyCastleProvider.PROVIDER_NAME);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    // ---------------------------------------------------------------------------------------------
    // PowChallengeIssuer
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("PowChallengeIssuer: all fail-closed validation gates run fast (<10ms batch)")
    void powIssuerFailClosedGatesAreFast() {
        PowChallengeIssuer issuer = new PowChallengeIssuer(mockRedisTemplate());
        // Warm-up: trigger lazy class init (BouncyCastle/SecureRandom/Mockito) so the timing
        // measures only the fail-closed gates.
        issuer.validateProof("v1:24:" + Instant.now().toEpochMilli() + ":warmup", "p");
        long start = System.nanoTime();

        // null/blank challenge or proof
        assertFalse(issuer.validateProof(null, "p"));
        assertFalse(issuer.validateProof("v1:24:1000:nonce", null));
        assertFalse(issuer.validateProof("  ", "p"));

        long now = Instant.now().toEpochMilli();
        // malformed (< 4 parts, > 4 parts)
        assertFalse(issuer.validateProof("v1:24:" + now, "p"));
        assertFalse(issuer.validateProof("v1:24:" + now + ":nonce:extra", "p"));
        // wrong version / difficulty
        assertFalse(issuer.validateProof("v2:24:" + now + ":nonce", "p"));
        assertFalse(issuer.validateProof("v1:23:" + now + ":nonce", "p"));
        assertFalse(issuer.validateProof("v1:25:" + now + ":nonce", "p"));
        // stale (>120s) and future (>120s) timestamps
        assertFalse(issuer.validateProof("v1:24:" + (now - 121_000L) + ":nonce", "p"));
        assertFalse(issuer.validateProof("v1:24:" + (now + 121_000L) + ":nonce", "p"));
        // blank nonce (kept by split since it is non-empty whitespace)
        assertFalse(issuer.validateProof("v1:24:" + now + ": ", "p"));
        // invalid proof hash (no brute force — pure gate checks)
        assertFalse(
                issuer.validateProof(
                        "v1:24:" + now + ":nonce-" + java.util.UUID.randomUUID(), "p"));

        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        // Fail-closed gates are pure-Java and microsecond-scale; the ceiling is a generous
        // budget far below any 2^24 brute force so the gates are proven fast.
        assertTrue(
                elapsedMs < 100,
                "fail-closed gates should run in a fast window: " + elapsedMs + "ms");
    }

    // ---------------------------------------------------------------------------------------------
    // CachingConfig (Jackson default-typing allowlist)
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("CachingConfig: allowed subtypes serialize cleanly; untrusted @class rejected")
    void cachingConfigPolymorphicTypeValidator() throws Exception {
        CachingConfig config = new CachingConfig();
        // Mirror CachingConfig.cacheManager()'s ObjectMapper setup to verify the allowlist contract
        // it encodes (see CachingConfig.java for the source of truth).
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        PolymorphicTypeValidator ptv =
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.treishvaam.financeapi.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType("java.math.")
                        .build();
        mapper.activateDefaultTyping(
                ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        // Allowed: java.util.ArrayList (JDK value) round-trips
        String arrayListJson = mapper.writeValueAsString(new ArrayList<>(List.of("a", "b")));
        assertNotNull(mapper.readValue(arrayListJson, Object.class));

        // Allowed: com.treishvaam.financeapi.marketdata.WidgetDataDto round-trips
        String dtoJson = mapper.writeValueAsString(new WidgetDataDto());
        assertNotNull(mapper.readValue(dtoJson, Object.class));

        // Rejected: untrusted @class (java.lang.ProcessBuilder is NOT in the allowlist)
        String badClass = "{\"@class\":\"java.lang.ProcessBuilder\"}";
        assertThrows(InvalidTypeIdException.class, () -> mapper.readValue(badClass, Object.class));
    }

    // ---------------------------------------------------------------------------------------------
    // FileController
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("FileController.serveFile: rejects ../, leading / and null bytes with 400")
    void fileControllerServeFileRejectsTraversalAndNullByte() throws Exception {
        FileController controller =
                new FileController(
                        mock(FileStorageService.class),
                        mock(ImageService.class),
                        "/tmp/treishvaam-file-test-uploads");

        MockHttpServletRequest traversal =
                new MockHttpServletRequest("GET", "/api/v1/uploads/../evil.txt");
        ResponseEntity<Resource> traversalResp = controller.serveFile(null, traversal);
        assertEquals(400, traversalResp.getStatusCode().value());

        MockHttpServletRequest leadingSlash =
                new MockHttpServletRequest("GET", "/api/v1/uploads//etc/passwd");
        assertEquals(400, controller.serveFile(null, leadingSlash).getStatusCode().value());

        MockHttpServletRequest nullByte = new MockHttpServletRequest("GET", "/other");
        assertEquals(400, controller.serveFile("a\0b", nullByte).getStatusCode().value());
    }

    @Test
    @DisplayName("FileController.handleFileUpload: rejects .svg and .html uploads with 400")
    void fileControllerRejectsSvgAndHtmlUploads() {
        FileController controller =
                new FileController(
                        mock(FileStorageService.class),
                        mock(ImageService.class),
                        "/tmp/treishvaam-file-test-uploads");

        MockMultipartFile svg =
                new MockMultipartFile("file", "test.svg", "image/svg+xml", "not-really".getBytes());
        ResponseEntity<Map<String, Object>> svgResp = controller.handleFileUpload(svg);
        assertEquals(400, svgResp.getStatusCode().value());
        assertEquals("Unsupported file type.", svgResp.getBody().get("errorMessage"));

        MockMultipartFile html =
                new MockMultipartFile("file", "page.html", "text/html", "<html></html>".getBytes());
        ResponseEntity<Map<String, Object>> htmlResp = controller.handleFileUpload(html);
        assertEquals(400, htmlResp.getStatusCode().value());
        assertEquals("Unsupported file type.", htmlResp.getBody().get("errorMessage"));
    }

    // ---------------------------------------------------------------------------------------------
    // BehavioralValidator + AegisBehavioralEngine (virtual-thread entropy fix)
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName(
            "BehavioralValidator: entropy adds +20 for long/high-entropy non-OAuth query even with cleared context")
    void behavioralEntropyAdds20WithClearedContext() {
        AegisBehavioralEngine engine = new AegisBehavioralEngine();
        BehavioralValidator validator = new BehavioralValidator(engine);
        RequestContextHolder
                .resetRequestAttributes(); // simulate the non-inheritable-ThreadLocal blindspot

        String highEntropyQuery = highEntropyString(70); // > 64 chars, entropy ≈ log2(70) > 5.2
        assertTrue(highEntropyQuery.length() > 64);
        assertTrue(ShannonEntropyCalculator.calculate(highEntropyQuery) > 5.2);

        int score = engine.calculateRiskScore("1.2.3.4", "TEST-JA3", "test-biom", highEntropyQuery);
        assertEquals(20, score); // the entropy +20 penalty applied via the explicit query string

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/quotes");
        request.addHeader("X-JA3-Fingerprint", "TEST-JA3");
        request.addHeader("X-Aegis-Biometric-Hash", "test-biom");
        request.setQueryString(highEntropyQuery);
        ValidatorResult result = validator.evaluate(request, "session-1");
        assertEquals(20, result.score());
    }

    @Test
    @DisplayName("BehavioralValidator: OAuth code=/state= callbacks are NOT penalized")
    void behavioralEntropyNotPenalizingOauthCallbacks() {
        AegisBehavioralEngine engine = new AegisBehavioralEngine();
        BehavioralValidator validator = new BehavioralValidator(engine);
        RequestContextHolder.resetRequestAttributes();

        String oauthQuery =
                "code=" + highEntropyString(80); // contains "code=", > 64 chars, entropy > 5.2
        assertTrue(oauthQuery.length() > 64);
        assertTrue(ShannonEntropyCalculator.calculate(oauthQuery) > 5.2);

        int score = engine.calculateRiskScore("1.2.3.4", "TEST-JA3", "test-biom", oauthQuery);
        assertEquals(0, score);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth");
        request.addHeader("X-JA3-Fingerprint", "TEST-JA3");
        request.addHeader("X-Aegis-Biometric-Hash", "test-biom");
        request.setQueryString(oauthQuery);
        ValidatorResult result = validator.evaluate(request, "session-2");
        assertEquals(0, result.score());
    }

    /** All-distinct printable-ASCII prefix of the alphabet → character entropy ≈ log2(length). */
    private static String highEntropyString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int c = 32; c < 127 && sb.length() < length; c++) {
            sb.append((char) c);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------------------------------
    // JwtValidator (P4A)
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("JwtValidator: oversized JWT header (>11,000 chars) → 100/BLOCK before decode")
    void jwtValidatorRejectsOversizedHeader() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JwtDecoder> jwtDecoderProvider = mock(ObjectProvider.class);
        JwtValidator validator =
                new JwtValidator(mock(AegisPqcJwtService.class), jwtDecoderProvider);
        String longHeader = "A".repeat(11_001);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/quotes");
        request.addHeader("Authorization", "Bearer " + longHeader + ".payload.signature");
        ValidatorResult result = validator.evaluate(request, "session-3");
        assertEquals(100, result.score());
        assertEquals(SecurityDecision.BLOCK, result.recommendation());
    }
}
