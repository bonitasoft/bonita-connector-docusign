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
class CreateFromTemplateConnectorTest {

    @Mock
    private DocuSignClient mockClient;

    private CreateFromTemplateConnector connector;

    @BeforeEach
    void setUp() {
        connector = new CreateFromTemplateConnector();
    }

    private Map<String, Object> validInputs() {
        var inputs = new HashMap<String, Object>();
        inputs.put("integrationKey", "test-integration-key");
        inputs.put("rsaPrivateKey", "test-rsa-key");
        inputs.put("userId", "test-user-id");
        inputs.put("accountId", "test-account-id");
        inputs.put("templateId", "template-123");
        inputs.put("templateRoles", "[{\"roleName\":\"Signer 1\",\"email\":\"john@example.com\",\"name\":\"John\"}]");
        return inputs;
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractDocuSignConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }

    @Test
    void should_execute_successfully() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.createFromTemplate(any(DocuSignConfiguration.class)))
                .thenReturn(new DocuSignClient.SendEnvelopeResult(
                        "env-456", "sent", "2026-03-25T10:00:00Z", "/envelopes/env-456"));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("envelopeId")).isEqualTo("env-456");
        assertThat(outputs.get("envelopeStatus")).isEqualTo("sent");
    }

    @Test
    void should_fail_validation_when_templateId_missing() {
        var inputs = validInputs();
        inputs.remove("templateId");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("templateId is mandatory");
    }

    @Test
    void should_fail_validation_when_templateRoles_missing() {
        var inputs = validInputs();
        inputs.remove("templateRoles");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("templateRoles is mandatory");
    }

    @Test
    void should_set_error_outputs_on_api_failure() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.createFromTemplate(any(DocuSignConfiguration.class)))
                .thenThrow(new DocuSignException("Template not found", 400, false));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage"))
                .contains("Template not found");
    }
}
