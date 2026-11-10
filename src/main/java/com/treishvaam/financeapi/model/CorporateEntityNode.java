package com.treishvaam.financeapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "corporate_entity_graph")
@Getter
@Setter
public class CorporateEntityNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version private Long version;

    @Column(name = "entity_key", nullable = false, unique = true, length = 100)
    private String entityKey;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "canonical_id", nullable = false, length = 255)
    private String canonicalId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "role_or_subtitle", length = 255)
    private String roleOrSubtitle;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "json_ld_payload", columnDefinition = "LONGTEXT", nullable = false)
    private String jsonLdPayload;

    @Column(name = "llms_txt_block", columnDefinition = "TEXT", nullable = false)
    private String llmsTxtBlock;

    @Column(name = "ai_feed_chunk", columnDefinition = "TEXT", nullable = false)
    private String aiFeedChunk;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "content_signature", length = 128)
    private String contentSignature;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
