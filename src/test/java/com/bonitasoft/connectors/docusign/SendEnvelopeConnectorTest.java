package com.bonitasoft.connectors.docusign;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class SendEnvelopeConnectorTest {

    @Mock
    private DocuSignClient mockClient;

    private SendEnvelopeConnector connector;

    @BeforeEach
    void setUp() {
        connector = new SendEnvelopeConnector();
    }

    private Map<String, Object> validInputs() {
        var inputs = new HashMap<String, Object>();
        inputs.put("integrationKey", "test-integration-key");
        inputs.put("rsaPrivateKey", "test-rsa-key");
        inputs.put("userId", "test-user-id");
        inputs.put("accountId", "test-account-id");
        inputs.put("documentBase64", "dGVzdA==");
        inputs.put("documentName", "contract.pdf");
        inputs.put("signerEmail", "signer@example.com");
        inputs.put("signerName", "John Doe");
        inputs.put("emailSubject", "Please sign this document");
        return inputs;
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractDocuSignConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }

    @Test
    void should_execute_successfully_when_all_mandatory_inputs_provided() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.sendEnvelope(any(DocuSignConfiguration.class)))
                .thenReturn(new DocuSignClient.SendEnvelopeResult(
                        "env-123", "sent", "2026-03-25T10:00:00Z", "/envelopes/env-123"));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("envelopeId")).isEqualTo("env-123");
        assertThat(outputs.get("envelopeStatus")).isEqualTo("sent");
        assertThat(outputs.get("statusDateTime")).isEqualTo("2026-03-25T10:00:00Z");
        assertThat(outputs.get("envelopeUri")).isEqualTo("/envelopes/env-123");
    }

    @Test
    void should_fail_validation_when_documentBase64_missing() {
        var inputs = validInputs();
        inputs.remove("documentBase64");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("documentBase64 is mandatory");
    }

    @Test
    void should_fail_validation_when_signerEmail_missing() {
        var inputs = validInputs();
        inputs.remove("signerEmail");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("signerEmail is mandatory");
    }

    @Test
    void should_fail_validation_when_signerName_missing() {
        var inputs = validInputs();
        inputs.remove("signerName");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("signerName is mandatory");
    }

    @Test
    void should_fail_validation_when_emailSubject_missing() {
        var inputs = validInputs();
        inputs.remove("emailSubject");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("emailSubject is mandatory");
    }

    @Test
    void should_fail_validation_when_integrationKey_missing() {
        var inputs = validInputs();
        inputs.remove("integrationKey");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("integrationKey is mandatory");
    }

    @Test
    void should_fail_validation_when_documentName_missing() {
        var inputs = validInputs();
        inputs.remove("documentName");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("documentName is mandatory");
    }

    @Test
    void should_set_error_outputs_on_api_failure() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.sendEnvelope(any(DocuSignConfiguration.class)))
                .thenThrow(new DocuSignException("Authentication failed", 401, false));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage"))
                .contains("Authentication failed");
    }

    @Test
    void should_set_error_outputs_on_unexpected_error() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.sendEnvelope(any(DocuSignConfiguration.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage"))
                .contains("Unexpected error");
    }

    @Test
    void should_apply_defaults_for_optional_inputs() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();

        var configField = AbstractDocuSignConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        var config = (DocuSignConfiguration) configField.get(connector);

        assertThat(config.getDocumentId()).isEqualTo("1");
        assertThat(config.getFileExtension()).isEqualTo("pdf");
        assertThat(config.getSignerRoutingOrder()).isEqualTo("1");
        assertThat(config.getExpirationDays()).isEqualTo(120);
        assertThat(config.getReminderEnabled()).isTrue();
        assertThat(config.getReminderDelayDays()).isEqualTo(3);
        assertThat(config.getReminderFrequencyDays()).isEqualTo(5);
    }
}
