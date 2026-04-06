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
class GetEnvelopeStatusConnectorTest {

    @Mock
    private DocuSignClient mockClient;

    private GetEnvelopeStatusConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GetEnvelopeStatusConnector();
    }

    private Map<String, Object> validInputs() {
        var inputs = new HashMap<String, Object>();
        inputs.put("integrationKey", "test-integration-key");
        inputs.put("rsaPrivateKey", "test-rsa-key");
        inputs.put("userId", "test-user-id");
        inputs.put("accountId", "test-account-id");
        inputs.put("envelopeId", "env-789");
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

        when(mockClient.getEnvelopeStatus("env-789"))
                .thenReturn(new DocuSignClient.GetEnvelopeStatusResult(
                        "completed", "2026-03-25T14:00:00Z", "2026-03-25T10:00:00Z",
                        "2026-03-25T14:00:00Z", "", "", "", "", 2));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("envelopeStatus")).isEqualTo("completed");
        assertThat(outputs.get("sentDateTime")).isEqualTo("2026-03-25T10:00:00Z");
        assertThat(outputs.get("completedDateTime")).isEqualTo("2026-03-25T14:00:00Z");
        assertThat(outputs.get("recipientCount")).isEqualTo(2);
    }

    @Test
    void should_fail_validation_when_envelopeId_missing() {
        var inputs = validInputs();
        inputs.remove("envelopeId");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("envelopeId is mandatory");
    }

    @Test
    void should_set_error_outputs_on_not_found() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.getEnvelopeStatus("env-789"))
                .thenThrow(new DocuSignException("Envelope not found", 404, false));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage"))
                .contains("Envelope not found");
    }
}
