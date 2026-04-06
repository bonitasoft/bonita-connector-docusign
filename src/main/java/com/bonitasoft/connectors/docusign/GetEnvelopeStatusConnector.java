package com.bonitasoft.connectors.docusign;

import lombok.extern.slf4j.Slf4j;

/**
 * [BETA] Retrieves the current status of a DocuSign envelope.
 * API: GET /v2.1/accounts/{accountId}/envelopes/{envelopeId}
 */
@Slf4j
public class GetEnvelopeStatusConnector extends AbstractDocuSignConnector {

    // Input parameter name constants
    static final String INPUT_ENVELOPE_ID = "envelopeId";

    // Output parameter name constants
    static final String OUTPUT_ENVELOPE_STATUS = "envelopeStatus";
    static final String OUTPUT_STATUS_DATE_TIME = "statusDateTime";
    static final String OUTPUT_SENT_DATE_TIME = "sentDateTime";
    static final String OUTPUT_COMPLETED_DATE_TIME = "completedDateTime";
    static final String OUTPUT_DECLINED_DATE_TIME = "declinedDateTime";
    static final String OUTPUT_VOIDED_DATE_TIME = "voidedDateTime";
    static final String OUTPUT_VOIDED_REASON = "voidedReason";
    static final String OUTPUT_EXPIRE_DATE_TIME = "expireDateTime";
    static final String OUTPUT_RECIPIENT_COUNT = "recipientCount";

    @Override
    protected DocuSignConfiguration buildConfiguration() {
        return baseConfigurationBuilder()
                .envelopeId(readStringInput(INPUT_ENVELOPE_ID))
                .build();
    }

    @Override
    protected void validateConfiguration(DocuSignConfiguration config) {
        super.validateConfiguration(config);
        if (config.getEnvelopeId() == null || config.getEnvelopeId().isBlank()) {
            throw new IllegalArgumentException("envelopeId is mandatory");
        }
    }

    @Override
    protected void doExecute() throws DocuSignException {
        log.info("Executing GetEnvelopeStatus connector");

        DocuSignClient.GetEnvelopeStatusResult result =
                client.getEnvelopeStatus(configuration.getEnvelopeId());

        setOutputParameter(OUTPUT_ENVELOPE_STATUS, result.envelopeStatus());
        setOutputParameter(OUTPUT_STATUS_DATE_TIME, result.statusDateTime());
        setOutputParameter(OUTPUT_SENT_DATE_TIME, result.sentDateTime());
        setOutputParameter(OUTPUT_COMPLETED_DATE_TIME, result.completedDateTime());
        setOutputParameter(OUTPUT_DECLINED_DATE_TIME, result.declinedDateTime());
        setOutputParameter(OUTPUT_VOIDED_DATE_TIME, result.voidedDateTime());
        setOutputParameter(OUTPUT_VOIDED_REASON, result.voidedReason());
        setOutputParameter(OUTPUT_EXPIRE_DATE_TIME, result.expireDateTime());
        setOutputParameter(OUTPUT_RECIPIENT_COUNT, result.recipientCount());

        log.info("GetEnvelopeStatus connector executed successfully, status={}",
                result.envelopeStatus());
    }
}
