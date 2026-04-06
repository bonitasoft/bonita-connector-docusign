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
class ListRecipientsConnectorTest {

    @Mock
    private DocuSignClient mockClient;

    private ListRecipientsConnector connector;

    @BeforeEach
    void setUp() {
        connector = new ListRecipientsConnector();
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

        when(mockClient.listRecipients("env-789"))
                .thenReturn(new DocuSignClient.ListRecipientsResult(
                        "[{\"recipientId\":\"1\",\"email\":\"john@example.com\"}]",
                        2, 1, false));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("signerCount")).isEqualTo(2);
        assertThat(outputs.get("completedSignerCount")).isEqualTo(1);
        assertThat(outputs.get("allSignersDone")).isEqualTo(false);
        assertThat((String) outputs.get("recipients")).contains("john@example.com");
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
}
