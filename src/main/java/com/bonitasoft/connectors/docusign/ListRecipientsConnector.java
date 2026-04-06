package com.bonitasoft.connectors.docusign;

import lombok.extern.slf4j.Slf4j;

/**
 * [BETA] Retrieves the list of all recipients and their signing status.
 * API: GET /v2.1/accounts/{accountId}/envelopes/{envelopeId}/recipients
 */
@Slf4j
public class ListRecipientsConnector extends AbstractDocuSignConnector {

    // Input parameter name constants
    static final String INPUT_ENVELOPE_ID = "envelopeId";

    // Output parameter name constants
    static final String OUTPUT_RECIPIENTS = "recipients";
    static final String OUTPUT_SIGNER_COUNT = "signerCount";
    static final String OUTPUT_COMPLETED_SIGNER_COUNT = "completedSignerCount";
    static final String OUTPUT_ALL_SIGNERS_DONE = "allSignersDone";

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
        log.info("Executing ListRecipients connector");

        DocuSignClient.ListRecipientsResult result =
                client.listRecipients(configuration.getEnvelopeId());

        setOutputParameter(OUTPUT_RECIPIENTS, result.recipients());
        setOutputParameter(OUTPUT_SIGNER_COUNT, result.signerCount());
        setOutputParameter(OUTPUT_COMPLETED_SIGNER_COUNT, result.completedSignerCount());
        setOutputParameter(OUTPUT_ALL_SIGNERS_DONE, result.allSignersDone());

        log.info("ListRecipients connector executed successfully, signerCount={}, completedSignerCount={}",
                result.signerCount(), result.completedSignerCount());
    }
}
