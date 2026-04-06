package com.bonitasoft.connectors.docusign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * API client facade for DocuSign eSignature REST API v2.1.
 * Uses java.net.http.HttpClient, Jackson, and Nimbus JOSE+JWT.
 */
@Slf4j
public class DocuSignClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TOKEN_LIFETIME_SECONDS = 3600;
    private static final long REFRESH_MARGIN_SECONDS = 300;

    private final DocuSignConfiguration configuration;
    private final RetryPolicy retryPolicy;
    private final HttpClient httpClient;

    private String accessToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public DocuSignClient(DocuSignConfiguration configuration) throws DocuSignException {
        this.configuration = configuration;
        this.retryPolicy = new RetryPolicy(configuration.getMaxRetries());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(configuration.getConnectTimeout()))
                .build();
        authenticate();
        log.debug("DocuSignClient initialized with base path: {}", configuration.getBasePath());
    }

    /**
     * Authenticates using OAuth2 JWT Grant flow.
     */
    void authenticate() throws DocuSignException {
        if (Instant.now().isBefore(tokenExpiry.minusSeconds(REFRESH_MARGIN_SECONDS))) {
            return;
        }
        try {
            String jwt = buildJwtAssertion();
            String tokenUrl = configuration.getOAuthBasePath() + "/oauth/token";

            String body = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + jwt;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofMillis(configuration.getReadTimeout()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new DocuSignException(
                        "Authentication failed (HTTP " + response.statusCode() + "): " + response.body(),
                        response.statusCode(), false);
            }

            JsonNode json = MAPPER.readTree(response.body());
            this.accessToken = json.get("access_token").asText();
            long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : TOKEN_LIFETIME_SECONDS;
            this.tokenExpiry = Instant.now().plusSeconds(expiresIn);

            log.info("DocuSign OAuth2 token obtained, expires in {}s", expiresIn);
        } catch (DocuSignException e) {
            throw e;
        } catch (Exception e) {
            throw new DocuSignException("Failed to authenticate with DocuSign: " + e.getMessage(), e);
        }
    }

    String buildJwtAssertion() throws DocuSignException {
        try {
            RSAPrivateKey privateKey = parseRsaPrivateKey(configuration.getRsaPrivateKey());

            String oAuthHost = URI.create(configuration.getOAuthBasePath()).getHost();

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer(configuration.getIntegrationKey())
                    .subject(configuration.getUserId())
                    .audience(oAuthHost)
                    .issueTime(java.util.Date.from(Instant.now()))
                    .expirationTime(java.util.Date.from(Instant.now().plusSeconds(TOKEN_LIFETIME_SECONDS)))
                    .claim("scope", "signature impersonation")
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    claimsSet);

            signedJWT.sign(new RSASSASigner(privateKey));

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new DocuSignException("Failed to build JWT assertion: " + e.getMessage(), e);
        }
    }

    static RSAPrivateKey parseRsaPrivateKey(String pem) throws DocuSignException {
        try {
            String cleaned = pem
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(cleaned);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new DocuSignException("Failed to parse RSA private key: " + e.getMessage(), e);
        }
    }

    // === Send Envelope ===
    public SendEnvelopeResult sendEnvelope(DocuSignConfiguration config) throws DocuSignException {
        return retryPolicy.execute(() -> {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("status", "sent");
            body.put("emailSubject", config.getEmailSubject());

            if (config.getEmailBody() != null && !config.getEmailBody().isBlank()) {
                body.put("emailBlurb", config.getEmailBody());
            }

            // Documents
            ArrayNode documents = body.putArray("documents");
            ObjectNode doc = documents.addObject();
            doc.put("documentBase64", config.getDocumentBase64());
            doc.put("name", config.getDocumentName());
            doc.put("documentId", config.getDocumentId());
            doc.put("fileExtension", config.getFileExtension());

            // Recipients
            ObjectNode recipients = body.putObject("recipients");

            // Signers
            ArrayNode signers = recipients.putArray("signers");
            ObjectNode primarySigner = signers.addObject();
            primarySigner.put("email", config.getSignerEmail());
            primarySigner.put("name", config.getSignerName());
            primarySigner.put("recipientId", "1");
            primarySigner.put("routingOrder", config.getSignerRoutingOrder());

            // SignHere tabs for primary signer
            if (config.getSignHereTabs() != null && !config.getSignHereTabs().isBlank()) {
                try {
                    JsonNode tabs = MAPPER.readTree(config.getSignHereTabs());
                    ObjectNode tabsNode = primarySigner.putObject("tabs");
                    tabsNode.set("signHereTabs", tabs);
                } catch (JsonProcessingException e) {
                    throw new DocuSignException("Invalid JSON for signHereTabs: " + e.getMessage());
                }
            }

            // Additional signers
            if (config.getAdditionalSigners() != null && !config.getAdditionalSigners().isBlank()) {
                try {
                    JsonNode additionalArr = MAPPER.readTree(config.getAdditionalSigners());
                    if (additionalArr.isArray()) {
                        int recipientId = 2;
                        for (JsonNode s : additionalArr) {
                            ObjectNode signer = signers.addObject();
                            signer.put("email", s.get("email").asText());
                            signer.put("name", s.get("name").asText());
                            signer.put("recipientId", String.valueOf(recipientId++));
                            if (s.has("routingOrder")) {
                                signer.put("routingOrder", s.get("routingOrder").asText());
                            }
                        }
                    }
                } catch (JsonProcessingException e) {
                    throw new DocuSignException("Invalid JSON for additionalSigners: " + e.getMessage());
                }
            }

            // CC recipients
            if (config.getCcRecipients() != null && !config.getCcRecipients().isBlank()) {
                try {
                    JsonNode ccArr = MAPPER.readTree(config.getCcRecipients());
                    if (ccArr.isArray()) {
                        ArrayNode carbonCopies = recipients.putArray("carbonCopies");
                        int recipientId = 100;
                        for (JsonNode cc : ccArr) {
                            ObjectNode ccNode = carbonCopies.addObject();
                            ccNode.put("email", cc.get("email").asText());
                            ccNode.put("name", cc.get("name").asText());
                            ccNode.put("recipientId", String.valueOf(recipientId++));
                            ccNode.put("routingOrder", "999");
                        }
                    }
                } catch (JsonProcessingException e) {
                    throw new DocuSignException("Invalid JSON for ccRecipients: " + e.getMessage());
                }
            }

            // Notification settings
            if (config.getExpirationDays() != null || Boolean.TRUE.equals(config.getReminderEnabled())) {
                ObjectNode notification = body.putObject("notification");
                notification.put("useAccountDefaults", "false");
                if (config.getExpirationDays() != null) {
                    ObjectNode expirations = notification.putObject("expirations");
                    expirations.put("expireEnabled", "true");
                    expirations.put("expireAfter", String.valueOf(config.getExpirationDays()));
                }
                if (Boolean.TRUE.equals(config.getReminderEnabled())) {
                    ObjectNode reminders = notification.putObject("reminders");
                    reminders.put("reminderEnabled", "true");
                    reminders.put("reminderDelay", String.valueOf(
                            config.getReminderDelayDays() != null ? config.getReminderDelayDays() : 3));
                    reminders.put("reminderFrequency", String.valueOf(
                            config.getReminderFrequencyDays() != null ? config.getReminderFrequencyDays() : 5));
                }
            }

            String jsonBody = MAPPER.writeValueAsString(body);
            String url = configuration.getBasePath() + "/v2.1/accounts/" + configuration.getAccountId() + "/envelopes";

            HttpResponse<String> response = doPost(url, jsonBody);
            handleErrorResponse(response, "send envelope");

            JsonNode result = MAPPER.readTree(response.body());
            return new SendEnvelopeResult(
                    result.path("envelopeId").asText(),
                    result.path("status").asText(),
                    result.path("statusDateTime").asText(),
                    result.path("uri").asText());
        });
    }

    // === Create from Template ===
    public SendEnvelopeResult createFromTemplate(DocuSignConfiguration config) throws DocuSignException {
        return retryPolicy.execute(() -> {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("status", "sent");
            body.put("templateId", config.getTemplateId());

            if (config.getEmailSubject() != null && !config.getEmailSubject().isBlank()) {
                body.put("emailSubject", config.getEmailSubject());
            }
            if (config.getEmailBody() != null && !config.getEmailBody().isBlank()) {
                body.put("emailBlurb", config.getEmailBody());
            }

            // Template roles
            if (config.getTemplateRoles() != null && !config.getTemplateRoles().isBlank()) {
                try {
                    JsonNode roles = MAPPER.readTree(config.getTemplateRoles());
                    body.set("templateRoles", roles);
                } catch (JsonProcessingException e) {
                    throw new DocuSignException("Invalid JSON for templateRoles: " + e.getMessage());
                }
            }

            // Template variables (custom fields / text tabs)
            if (config.getTemplateVariables() != null && !config.getTemplateVariables().isBlank()) {
                try {
                    JsonNode vars = MAPPER.readTree(config.getTemplateVariables());
                    ObjectNode customFields = body.putObject("customFields");
                    ArrayNode textCustomFields = customFields.putArray("textCustomFields");
                    vars.fields().forEachRemaining(entry -> {
                        ObjectNode field = textCustomFields.addObject();
                        field.put("name", entry.getKey());
                        field.put("value", entry.getValue().asText());
                        field.put("show", "false");
                    });
                } catch (JsonProcessingException e) {
                    throw new DocuSignException("Invalid JSON for templateVariables: " + e.getMessage());
                }
            }

            String jsonBody = MAPPER.writeValueAsString(body);
            String url = configuration.getBasePath() + "/v2.1/accounts/" + configuration.getAccountId() + "/envelopes";

            HttpResponse<String> response = doPost(url, jsonBody);
            handleErrorResponse(response, "create from template");

            JsonNode result = MAPPER.readTree(response.body());
            return new SendEnvelopeResult(
                    result.path("envelopeId").asText(),
                    result.path("status").asText(),
                    result.path("statusDateTime").asText(),
                    result.path("uri").asText());
        });
    }

    // === Get Envelope Status ===
    public GetEnvelopeStatusResult getEnvelopeStatus(String envelopeId) throws DocuSignException {
        return retryPolicy.execute(() -> {
            String url = configuration.getBasePath() + "/v2.1/accounts/" + configuration.getAccountId()
                    + "/envelopes/" + envelopeId;

            HttpResponse<String> response = doGet(url);
            handleErrorResponse(response, "get envelope status");

            JsonNode result = MAPPER.readTree(response.body());
            return new GetEnvelopeStatusResult(
                    result.path("status").asText(),
                    result.path("statusChangedDateTime").asText(),
                    result.path("sentDateTime").asText(),
                    result.path("completedDateTime").asText(""),
                    result.path("declinedDateTime").asText(""),
                    result.path("voidedDateTime").asText(""),
                    result.path("voidedReason").asText(""),
                    result.path("expireDateTime").asText(""),
                    result.path("recipientsUri").asText().isEmpty() ? 0 :
                            result.path("recipients").size());
        });
    }

    // === List Recipients ===
    public ListRecipientsResult listRecipients(String envelopeId) throws DocuSignException {
        return retryPolicy.execute(() -> {
            String url = configuration.getBasePath() + "/v2.1/accounts/" + configuration.getAccountId()
                    + "/envelopes/" + envelopeId + "/recipients";

            HttpResponse<String> response = doGet(url);
            handleErrorResponse(response, "list recipients");

            JsonNode result = MAPPER.readTree(response.body());

            ArrayNode recipientList = MAPPER.createArrayNode();
            int signerCount = 0;
            int completedSignerCount = 0;

            // Process signers
            if (result.has("signers")) {
                for (JsonNode signer : result.get("signers")) {
                    ObjectNode recipient = MAPPER.createObjectNode();
                    recipient.put("recipientId", signer.path("recipientId").asText());
                    recipient.put("recipientType", "signer");
                    recipient.put("email", signer.path("email").asText());
                    recipient.put("name", signer.path("name").asText());
                    recipient.put("routingOrder", signer.path("routingOrder").asText());
                    recipient.put("status", signer.path("status").asText());
                    recipient.put("signedDateTime", signer.path("signedDateTime").asText(""));
                    recipient.put("deliveredDateTime", signer.path("deliveredDateTime").asText(""));
                    recipient.put("declinedDateTime", signer.path("declinedDateTime").asText(""));
                    recipient.put("declineReason", signer.path("declinedReason").asText(""));
                    recipientList.add(recipient);
                    signerCount++;
                    String status = signer.path("status").asText();
                    if ("completed".equals(status) || "signed".equals(status)) {
                        completedSignerCount++;
                    }
                }
            }

            // Process carbon copies
            if (result.has("carbonCopies")) {
                for (JsonNode cc : result.get("carbonCopies")) {
                    ObjectNode recipient = MAPPER.createObjectNode();
                    recipient.put("recipientId", cc.path("recipientId").asText());
                    recipient.put("recipientType", "cc");
                    recipient.put("email", cc.path("email").asText());
                    recipient.put("name", cc.path("name").asText());
                    recipient.put("routingOrder", cc.path("routingOrder").asText());
                    recipient.put("status", cc.path("status").asText());
                    recipient.put("signedDateTime", "");
                    recipient.put("deliveredDateTime", cc.path("deliveredDateTime").asText(""));
                    recipient.put("declinedDateTime", "");
                    recipient.put("declineReason", "");
                    recipientList.add(recipient);
                }
            }

            String recipientsJson = MAPPER.writeValueAsString(recipientList);
            return new ListRecipientsResult(recipientsJson, signerCount, completedSignerCount,
                    signerCount > 0 && completedSignerCount == signerCount);
        });
    }

    // === Download Document ===
    public DownloadDocumentResult downloadDocument(String envelopeId, String documentId)
            throws DocuSignException {
        return retryPolicy.execute(() -> {
            String url = configuration.getBasePath() + "/v2.1/accounts/" + configuration.getAccountId()
                    + "/envelopes/" + envelopeId + "/documents/" + documentId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/pdf")
                    .GET()
                    .timeout(Duration.ofMillis(configuration.getReadTimeout()))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 400) {
                String errorBody = new String(response.body(), StandardCharsets.UTF_8);
                handleErrorByStatusCode(response.statusCode(), errorBody, "download document");
            }

            byte[] content = response.body();
            String base64Content = Base64.getEncoder().encodeToString(content);

            String contentType = response.headers().firstValue("Content-Type").orElse("application/pdf");
            String contentDisposition = response.headers().firstValue("Content-Disposition").orElse("");
            String fileName = extractFileName(contentDisposition, documentId);

            if (content.length > 50_000_000) {
                log.warn("Downloaded document is very large: {} bytes. Consider writing to external storage.",
                        content.length);
            }

            return new DownloadDocumentResult(base64Content, fileName, contentType, (long) content.length);
        });
    }

    // === Void Envelope ===
    public VoidEnvelopeResult voidEnvelope(String envelopeId, String voidedReason) throws DocuSignException {
        return retryPolicy.execute(() -> {
            String url = configuration.getBasePath() + "/v2.1/accounts/" + configuration.getAccountId()
                    + "/envelopes/" + envelopeId;

            ObjectNode body = MAPPER.createObjectNode();
            body.put("status", "voided");
            body.put("voidedReason", voidedReason);

            String jsonBody = MAPPER.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofMillis(configuration.getReadTimeout()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Already voided - treat as idempotent success
            if (response.statusCode() == 400) {
                JsonNode error = MAPPER.readTree(response.body());
                String errorCode = error.path("errorCode").asText("");
                if ("ENVELOPE_IS_VOIDED".equalsIgnoreCase(errorCode)
                        || "ENVELOPE_ALREADY_VOIDED".equalsIgnoreCase(errorCode)) {
                    return new VoidEnvelopeResult(envelopeId, "voided");
                }
                // Completed envelope cannot be voided
                if ("ENVELOPE_IS_COMPLETED".equalsIgnoreCase(errorCode)
                        || "ENVELOPE_INVALID_STATUS".equalsIgnoreCase(errorCode)) {
                    throw new DocuSignException(
                            "Cannot void envelope: envelope is already completed", 400, false);
                }
            }

            if (response.statusCode() >= 400) {
                handleErrorByStatusCode(response.statusCode(), response.body(), "void envelope");
            }

            return new VoidEnvelopeResult(envelopeId, "voided");
        });
    }

    // === HTTP helpers ===

    private HttpResponse<String> doPost(String url, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofMillis(configuration.getReadTimeout()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> doGet(String url)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofMillis(configuration.getReadTimeout()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void handleErrorResponse(HttpResponse<String> response, String operation)
            throws DocuSignException {
        if (response.statusCode() >= 400) {
            handleErrorByStatusCode(response.statusCode(), response.body(), operation);
        }
    }

    private void handleErrorByStatusCode(int statusCode, String body, String operation)
            throws DocuSignException {
        String errorCode = "";
        String errorMessage = body;

        try {
            JsonNode error = MAPPER.readTree(body);
            errorCode = error.path("errorCode").asText("");
            errorMessage = error.path("message").asText(body);
        } catch (Exception ignored) {
            // Use raw body as message
        }

        boolean retryable = RetryPolicy.isRetryableStatusCode(statusCode);

        if (statusCode == 401) {
            // Try to refresh token
            try {
                this.tokenExpiry = Instant.EPOCH;
                authenticate();
                // Token refreshed - caller should retry via RetryPolicy
            } catch (DocuSignException e) {
                throw new DocuSignException(
                        "Authentication failed - verify RSA key and consent: " + errorMessage,
                        statusCode, false);
            }
            throw new DocuSignException(
                    "Authentication token expired, retrying: " + errorMessage,
                    statusCode, true);
        }

        throw new DocuSignException(
                "DocuSign API error (" + operation + "): [" + errorCode + "] " + errorMessage,
                statusCode, retryable);
    }

    private String extractFileName(String contentDisposition, String documentId) {
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            String[] parts = contentDisposition.split("filename=");
            if (parts.length > 1) {
                return parts[1].replace("\"", "").trim();
            }
        }
        return "document-" + documentId + ".pdf";
    }

    // === Result records ===

    public record SendEnvelopeResult(String envelopeId, String envelopeStatus,
                                     String statusDateTime, String envelopeUri) {}

    public record GetEnvelopeStatusResult(String envelopeStatus, String statusDateTime,
                                          String sentDateTime, String completedDateTime,
                                          String declinedDateTime, String voidedDateTime,
                                          String voidedReason, String expireDateTime,
                                          int recipientCount) {}

    public record ListRecipientsResult(String recipients, int signerCount,
                                       int completedSignerCount, boolean allSignersDone) {}

    public record DownloadDocumentResult(String documentContent, String documentName,
                                         String contentType, long contentLength) {}

    public record VoidEnvelopeResult(String envelopeId, String envelopeStatus) {}
}
