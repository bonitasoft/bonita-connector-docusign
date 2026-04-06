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
class VoidEnvelopeConnectorTest {

    @Mock
    private DocuSignClient mockClient;

    private VoidEnvelopeConnector connector;

    @BeforeEach
    void setUp() {
        connector = new VoidEnvelopeConnector();
    }

    private Map<String, Object> validInputs() {
        var inputs = new HashMap<String, Object>();
        inputs.put("integrationKey", "test-integration-key");
        inputs.put("rsaPrivateKey", "test-rsa-key");
        inputs.put("userId", "test-user-id");
        inputs.put("accountId", "test-account-id");
        inputs.put("envelopeId", "env-789");
        inputs.put("voidedReason", "Contract terms updated");
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

        when(mockClient.voidEnvelope("env-789", "Contract terms updated"))
                .thenReturn(new DocuSignClient.VoidEnvelopeResult("env-789", "voided"));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("envelopeId")).isEqualTo("env-789");
        assertThat(outputs.get("envelopeStatus")).isEqualTo("voided");
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
    void should_fail_validation_when_voidedReason_missing() {
        var inputs = validInputs();
        inputs.remove("voidedReason");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("voidedReason is mandatory");
    }

    @Test
    void should_set_error_on_completed_envelope() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.voidEnvelope("env-789", "Contract terms updated"))
                .thenThrow(new DocuSignException(
                        "Cannot void envelope: envelope is already completed", 400, false));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage"))
                .contains("already completed");
    }
}
