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
class DownloadDocumentConnectorTest {

    @Mock
    private DocuSignClient mockClient;

    private DownloadDocumentConnector connector;

    @BeforeEach
    void setUp() {
        connector = new DownloadDocumentConnector();
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
    void should_execute_successfully_with_default_documentId() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.downloadDocument("env-789", "combined"))
                .thenReturn(new DocuSignClient.DownloadDocumentResult(
                        "dGVzdA==", "contract.pdf", "application/pdf", 4L));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("documentContent")).isEqualTo("dGVzdA==");
        assertThat(outputs.get("documentName")).isEqualTo("contract.pdf");
        assertThat(outputs.get("contentType")).isEqualTo("application/pdf");
        assertThat(outputs.get("contentLength")).isEqualTo(4L);
    }

    @Test
    void should_use_custom_documentId() throws Exception {
        var inputs = validInputs();
        inputs.put("documentId", "certificate");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.downloadDocument("env-789", "certificate"))
                .thenReturn(new DocuSignClient.DownloadDocumentResult(
                        "Y2VydA==", "certificate.pdf", "application/pdf", 8L));

        var outputs = connector.execute();

        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("documentName")).isEqualTo("certificate.pdf");
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
