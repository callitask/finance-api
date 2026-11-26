package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.dto.FaroPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Asynchronous, timeout-bounded forwarding of Grafana Faro telemetry payloads to
 * Alloy collector.
 *
 * <p>Scope: - Extracted from MonitoringController to eliminate Spring AOP @Async self-invocation
 * bypass and unbounded RestTemplate hangs.
 *
 * <p>Security Constraints: - Never throws to Tomcat caller threads; connect/read timeouts fixed at
 * 3000ms.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - CREATED (2026-11-25 - Batch 1 §1.2 Async Alloy
 * Forwarding Service): • Extracted @Async forwardToAlloy(FaroPayload) from MonitoringController
 * into dedicated @Service bean so Spring @Async proxy executes off Tomcat request threads. •
 * Configured RestTemplate with SimpleClientHttpRequestFactory (connectTimeout=3000ms,
 * readTimeout=3000ms) and configurable monitoring.alloy.url.
 */
@Service
public class AlloyForwardingService {

    private static final Logger logger = LoggerFactory.getLogger(AlloyForwardingService.class);

    private final RestTemplate restTemplate;
    private final String alloyUrl;

    public AlloyForwardingService(
            @Value("${monitoring.alloy.url:http://alloy:12347/collect}") String alloyUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(requestFactory);
        this.alloyUrl = alloyUrl;
    }

    @Async
    public void forwardToAlloy(FaroPayload payload) {
        if (payload == null) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<FaroPayload> request = new HttpEntity<>(payload, headers);
            restTemplate.postForLocation(alloyUrl, request);
        } catch (Exception e) {
            logger.debug("Alloy forward failed: {}", e.getMessage());
        }
    }
}
