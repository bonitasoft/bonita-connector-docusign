package com.bonitasoft.connectors.docusign;

import lombok.extern.slf4j.Slf4j;

/**
 * [BETA] Voids (cancels) a pending envelope that has not yet been completed.
 * API: PUT /v2.1/accounts/{accountId}/envelopes/{envelopeId}
 */
@Slf4j
public class VoidEnvelopeConnector extends AbstractDocuSignConnector {

    // Input parameter name constants
    static final String INPUT_ENVELOPE_ID = "envelopeId";
    static final String INPUT_VOIDED_REASON = "voidedReason";

    // Output parameter name constants
    static final String OUTPUT_ENVELOPE_ID = "envelopeId";
    static final String OUTPUT_ENVELOPE_STATUS = "envelopeStatus";

    @Override
    protected DocuSignConfiguration buildConfiguration() {
        return baseConfigurationBuilder()
                .envelopeId(readStringInput(INPUT_ENVELOPE_ID))
                .voidedReason(readStringInput(INPUT_VOIDED_REASON))
                .build();
    }

    @Override
    protected void validateConfiguration(DocuSignConfiguration config) {
        super.validateConfiguration(config);
        if (config.getEnvelopeId() == null || config.getEnvelopeId().isBlank()) {
            throw new IllegalArgumentException("envelopeId is mandatory");
        }
        if (config.getVoidedReason() == null || config.getVoidedReason().isBlank()) {
            throw new IllegalArgumentException("voidedReason is mandatory");
        }
    }

    @Override
    protected void doExecute() throws DocuSignException {
        log.info("Executing VoidEnvelope connector");

        DocuSignClient.VoidEnvelopeResult result =
                client.voidEnvelope(configuration.getEnvelopeId(),
                        configuration.getVoidedReason());

        setOutputParameter(OUTPUT_ENVELOPE_ID, result.envelopeId());
        setOutputParameter(OUTPUT_ENVELOPE_STATUS, result.envelopeStatus());

        log.info("VoidEnvelope connector executed successfully, envelopeId={}",
                result.envelopeId());
    }
}
