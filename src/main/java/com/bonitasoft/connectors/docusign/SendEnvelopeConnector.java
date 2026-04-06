package com.bonitasoft.connectors.docusign;

import lombok.extern.slf4j.Slf4j;

/**
 * [BETA] Creates and sends an envelope containing one or more documents to signers.
 * API: POST /v2.1/accounts/{accountId}/envelopes
 */
@Slf4j
public class SendEnvelopeConnector extends AbstractDocuSignConnector {

    // Input parameter name constants
    static final String INPUT_DOCUMENT_BASE64 = "documentBase64";
    static final String INPUT_DOCUMENT_NAME = "documentName";
    static final String INPUT_DOCUMENT_ID = "documentId";
    static final String INPUT_FILE_EXTENSION = "fileExtension";
    static final String INPUT_SIGNER_EMAIL = "signerEmail";
    static final String INPUT_SIGNER_NAME = "signerName";
    static final String INPUT_SIGNER_ROUTING_ORDER = "signerRoutingOrder";
    static final String INPUT_ADDITIONAL_SIGNERS = "additionalSigners";
    static final String INPUT_CC_RECIPIENTS = "ccRecipients";
    static final String INPUT_EMAIL_SUBJECT = "emailSubject";
    static final String INPUT_EMAIL_BODY = "emailBody";
    static final String INPUT_SIGN_HERE_TABS = "signHereTabs";
    static final String INPUT_EXPIRATION_DAYS = "expirationDays";
    static final String INPUT_REMINDER_ENABLED = "reminderEnabled";
    static final String INPUT_REMINDER_DELAY_DAYS = "reminderDelayDays";
    static final String INPUT_REMINDER_FREQUENCY_DAYS = "reminderFrequencyDays";

    // Output parameter name constants
    static final String OUTPUT_ENVELOPE_ID = "envelopeId";
    static final String OUTPUT_ENVELOPE_STATUS = "envelopeStatus";
    static final String OUTPUT_STATUS_DATE_TIME = "statusDateTime";
    static final String OUTPUT_ENVELOPE_URI = "envelopeUri";

    @Override
    protected DocuSignConfiguration buildConfiguration() {
        return baseConfigurationBuilder()
                .documentBase64(readStringInput(INPUT_DOCUMENT_BASE64))
                .documentName(readStringInput(INPUT_DOCUMENT_NAME))
                .documentId(readStringInput(INPUT_DOCUMENT_ID, "1"))
                .fileExtension(readStringInput(INPUT_FILE_EXTENSION, "pdf"))
                .signerEmail(readStringInput(INPUT_SIGNER_EMAIL))
                .signerName(readStringInput(INPUT_SIGNER_NAME))
                .signerRoutingOrder(readStringInput(INPUT_SIGNER_ROUTING_ORDER, "1"))
                .additionalSigners(readStringInput(INPUT_ADDITIONAL_SIGNERS))
                .ccRecipients(readStringInput(INPUT_CC_RECIPIENTS))
                .emailSubject(readStringInput(INPUT_EMAIL_SUBJECT))
                .emailBody(readStringInput(INPUT_EMAIL_BODY))
                .signHereTabs(readStringInput(INPUT_SIGN_HERE_TABS))
                .expirationDays(getInputParameter(INPUT_EXPIRATION_DAYS) != null
                        ? readIntegerInput(INPUT_EXPIRATION_DAYS, 120) : 120)
                .reminderEnabled(readBooleanInput(INPUT_REMINDER_ENABLED, true))
                .reminderDelayDays(readIntegerInput(INPUT_REMINDER_DELAY_DAYS, 3))
                .reminderFrequencyDays(readIntegerInput(INPUT_REMINDER_FREQUENCY_DAYS, 5))
                .build();
    }

    @Override
    protected void validateConfiguration(DocuSignConfiguration config) {
        super.validateConfiguration(config);
        if (config.getDocumentBase64() == null || config.getDocumentBase64().isBlank()) {
            throw new IllegalArgumentException("documentBase64 is mandatory");
        }
        if (config.getDocumentName() == null || config.getDocumentName().isBlank()) {
            throw new IllegalArgumentException("documentName is mandatory");
        }
        if (config.getSignerEmail() == null || config.getSignerEmail().isBlank()) {
            throw new IllegalArgumentException("signerEmail is mandatory");
        }
        if (config.getSignerName() == null || config.getSignerName().isBlank()) {
            throw new IllegalArgumentException("signerName is mandatory");
        }
        if (config.getEmailSubject() == null || config.getEmailSubject().isBlank()) {
            throw new IllegalArgumentException("emailSubject is mandatory");
        }
    }

    @Override
    protected void doExecute() throws DocuSignException {
        log.info("Executing SendEnvelope connector");

        DocuSignClient.SendEnvelopeResult result = client.sendEnvelope(configuration);

        setOutputParameter(OUTPUT_ENVELOPE_ID, result.envelopeId());
        setOutputParameter(OUTPUT_ENVELOPE_STATUS, result.envelopeStatus());
        setOutputParameter(OUTPUT_STATUS_DATE_TIME, result.statusDateTime());
        setOutputParameter(OUTPUT_ENVELOPE_URI, result.envelopeUri());

        log.info("SendEnvelope connector executed successfully, envelopeId={}", result.envelopeId());
    }
}
