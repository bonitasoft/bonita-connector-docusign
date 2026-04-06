package com.bonitasoft.connectors.docusign;

import lombok.extern.slf4j.Slf4j;

/**
 * [BETA] Downloads a document from a completed envelope as Base64-encoded content.
 * API: GET /v2.1/accounts/{accountId}/envelopes/{envelopeId}/documents/{documentId}
 */
@Slf4j
public class DownloadDocumentConnector extends AbstractDocuSignConnector {

    // Input parameter name constants
    static final String INPUT_ENVELOPE_ID = "envelopeId";
    static final String INPUT_DOCUMENT_ID = "documentId";

    // Output parameter name constants
    static final String OUTPUT_DOCUMENT_CONTENT = "documentContent";
    static final String OUTPUT_DOCUMENT_NAME = "documentName";
    static final String OUTPUT_CONTENT_TYPE = "contentType";
    static final String OUTPUT_CONTENT_LENGTH = "contentLength";

    @Override
    protected DocuSignConfiguration buildConfiguration() {
        return baseConfigurationBuilder()
                .envelopeId(readStringInput(INPUT_ENVELOPE_ID))
                .downloadDocumentId(readStringInput(INPUT_DOCUMENT_ID, "combined"))
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
        log.info("Executing DownloadDocument connector");

        DocuSignClient.DownloadDocumentResult result =
                client.downloadDocument(configuration.getEnvelopeId(),
                        configuration.getDownloadDocumentId());

        setOutputParameter(OUTPUT_DOCUMENT_CONTENT, result.documentContent());
        setOutputParameter(OUTPUT_DOCUMENT_NAME, result.documentName());
        setOutputParameter(OUTPUT_CONTENT_TYPE, result.contentType());
        setOutputParameter(OUTPUT_CONTENT_LENGTH, result.contentLength());

        log.info("DownloadDocument connector executed successfully, fileName={}, size={}",
                result.documentName(), result.contentLength());
    }
}
