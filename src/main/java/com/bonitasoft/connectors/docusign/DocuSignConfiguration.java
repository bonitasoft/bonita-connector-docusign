package com.bonitasoft.connectors.docusign;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration for DocuSign connector.
 * Holds connection/auth parameters and operation-specific parameters.
 */
@Data
@Builder
public class DocuSignConfiguration {

    // === Connection / Auth parameters (Project/Runtime scope) ===
    private String integrationKey;
    private String rsaPrivateKey;
    private String userId;
    private String accountId;

    @Builder.Default
    private String basePath = "https://www.docusign.net/restapi";

    @Builder.Default
    private String oAuthBasePath = "https://account.docusign.com";

    @Builder.Default
    private int connectTimeout = 30000;

    @Builder.Default
    private int readTimeout = 60000;

    // === SendEnvelope parameters ===
    private String documentBase64;
    private String documentName;
    @Builder.Default
    private String documentId = "1";
    @Builder.Default
    private String fileExtension = "pdf";
    private String signerEmail;
    private String signerName;
    @Builder.Default
    private String signerRoutingOrder = "1";
    private String additionalSigners;
    private String ccRecipients;
    private String emailSubject;
    private String emailBody;
    private String signHereTabs;
    @Builder.Default
    private Integer expirationDays = 120;
    @Builder.Default
    private Boolean reminderEnabled = true;
    @Builder.Default
    private Integer reminderDelayDays = 3;
    @Builder.Default
    private Integer reminderFrequencyDays = 5;

    // === CreateFromTemplate parameters ===
    private String templateId;
    private String templateRoles;
    private String templateVariables;

    // === GetEnvelopeStatus / ListRecipients / DownloadDocument / VoidEnvelope ===
    private String envelopeId;

    // === DownloadDocument ===
    @Builder.Default
    private String downloadDocumentId = "combined";

    // === VoidEnvelope ===
    private String voidedReason;

    // === Advanced ===
    @Builder.Default
    private int maxRetries = 3;

    /**
     * Resolves a parameter value from input, then system property, then env var.
     */
    public static String resolveParam(java.util.Map<String, Object> params, String key, String envVar) {
        Object raw = params.get(key);
        String value = raw != null ? raw.toString() : null;
        if (value == null || value.isBlank()) {
            value = System.getProperty("docusign." + key);
        }
        if (value == null || value.isBlank()) {
            value = System.getenv(envVar);
        }
        return value;
    }
}
