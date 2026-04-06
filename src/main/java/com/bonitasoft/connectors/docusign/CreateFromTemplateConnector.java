package com.bonitasoft.connectors.docusign;

import lombok.extern.slf4j.Slf4j;

/**
 * [BETA] Creates and sends an envelope using a pre-configured DocuSign template.
 * API: POST /v2.1/accounts/{accountId}/envelopes (with templateId)
 */
@Slf4j
public class CreateFromTemplateConnector extends AbstractDocuSignConnector {

    // Input parameter name constants
    static final String INPUT_TEMPLATE_ID = "templateId";
    static final String INPUT_TEMPLATE_ROLES = "templateRoles";
    static final String INPUT_TEMPLATE_VARIABLES = "templateVariables";
    static final String INPUT_EMAIL_SUBJECT = "emailSubject";
    static final String INPUT_EMAIL_BODY = "emailBody";

    // Output parameter name constants
    static final String OUTPUT_ENVELOPE_ID = "envelopeId";
    static final String OUTPUT_ENVELOPE_STATUS = "envelopeStatus";
    static final String OUTPUT_STATUS_DATE_TIME = "statusDateTime";
    static final String OUTPUT_ENVELOPE_URI = "envelopeUri";

    @Override
    protected DocuSignConfiguration buildConfiguration() {
        return baseConfigurationBuilder()
                .templateId(readStringInput(INPUT_TEMPLATE_ID))
                .templateRoles(readStringInput(INPUT_TEMPLATE_ROLES))
                .templateVariables(readStringInput(INPUT_TEMPLATE_VARIABLES))
                .emailSubject(readStringInput(INPUT_EMAIL_SUBJECT))
                .emailBody(readStringInput(INPUT_EMAIL_BODY))
                .build();
    }

    @Override
    protected void validateConfiguration(DocuSignConfiguration config) {
        super.validateConfiguration(config);
        if (config.getTemplateId() == null || config.getTemplateId().isBlank()) {
            throw new IllegalArgumentException("templateId is mandatory");
        }
        if (config.getTemplateRoles() == null || config.getTemplateRoles().isBlank()) {
            throw new IllegalArgumentException("templateRoles is mandatory");
        }
    }

    @Override
    protected void doExecute() throws DocuSignException {
        log.info("Executing CreateFromTemplate connector");

        DocuSignClient.SendEnvelopeResult result = client.createFromTemplate(configuration);

        setOutputParameter(OUTPUT_ENVELOPE_ID, result.envelopeId());
        setOutputParameter(OUTPUT_ENVELOPE_STATUS, result.envelopeStatus());
        setOutputParameter(OUTPUT_STATUS_DATE_TIME, result.statusDateTime());
        setOutputParameter(OUTPUT_ENVELOPE_URI, result.envelopeUri());

        log.info("CreateFromTemplate connector executed successfully, envelopeId={}", result.envelopeId());
    }
}
