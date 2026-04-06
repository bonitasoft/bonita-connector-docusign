package com.bonitasoft.connectors.docusign;

import lombok.extern.slf4j.Slf4j;
import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

/**
 * [BETA] Abstract base connector for DocuSign eSignature.
 * Handles connection lifecycle, validation, and error handling.
 */
@Slf4j
public abstract class AbstractDocuSignConnector extends AbstractConnector {

    // Connection parameter constants
    static final String INPUT_INTEGRATION_KEY = "integrationKey";
    static final String INPUT_RSA_PRIVATE_KEY = "rsaPrivateKey";
    static final String INPUT_USER_ID = "userId";
    static final String INPUT_ACCOUNT_ID = "accountId";
    static final String INPUT_BASE_PATH = "basePath";
    static final String INPUT_OAUTH_BASE_PATH = "oAuthBasePath";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";

    // Output parameter constants
    protected static final String OUTPUT_SUCCESS = "success";
    protected static final String OUTPUT_ERROR_MESSAGE = "errorMessage";

    protected DocuSignConfiguration configuration;
    protected DocuSignClient client;

    /**
     * Validates all input parameters and builds the configuration object.
     * Called by Bonita engine before connect().
     */
    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        try {
            this.configuration = buildConfiguration();
            validateConfiguration(this.configuration);
        } catch (IllegalArgumentException e) {
            throw new ConnectorValidationException(this, e.getMessage());
        }
    }

    /**
     * Creates and authenticates the API client.
     */
    @Override
    public void connect() throws ConnectorException {
        try {
            this.client = new DocuSignClient(this.configuration);
            log.info("DocuSign connector connected successfully");
        } catch (DocuSignException e) {
            throw new ConnectorException("Failed to connect: " + e.getMessage(), e);
        }
    }

    /**
     * Stateless HTTP -- null the client reference.
     */
    @Override
    public void disconnect() throws ConnectorException {
        this.client = null;
    }

    /**
     * Template method: wraps doExecute() with standard error handling.
     * Always sets success and errorMessage outputs.
     */
    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        try {
            doExecute();
            setOutputParameter(OUTPUT_SUCCESS, true);
        } catch (DocuSignException e) {
            log.error("DocuSign connector execution failed: {}", e.getMessage(), e);
            setOutputParameter(OUTPUT_SUCCESS, false);
            setOutputParameter(OUTPUT_ERROR_MESSAGE, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in DocuSign connector: {}", e.getMessage(), e);
            setOutputParameter(OUTPUT_SUCCESS, false);
            setOutputParameter(OUTPUT_ERROR_MESSAGE, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Subclasses implement this to perform their specific operation.
     */
    protected abstract void doExecute() throws DocuSignException;

    /**
     * Subclasses implement this to build their configuration from input parameters.
     */
    protected abstract DocuSignConfiguration buildConfiguration();

    /**
     * Validates the configuration. Checks mandatory auth parameters.
     */
    protected void validateConfiguration(DocuSignConfiguration config) {
        if (config.getIntegrationKey() == null || config.getIntegrationKey().isBlank()) {
            throw new IllegalArgumentException("integrationKey is mandatory");
        }
        if (config.getRsaPrivateKey() == null || config.getRsaPrivateKey().isBlank()) {
            throw new IllegalArgumentException("rsaPrivateKey is mandatory");
        }
        if (config.getUserId() == null || config.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId is mandatory");
        }
        if (config.getAccountId() == null || config.getAccountId().isBlank()) {
            throw new IllegalArgumentException("accountId is mandatory");
        }
    }

    /** Helper: read a String input, returning null if not set. */
    protected String readStringInput(String name) {
        Object value = getInputParameter(name);
        return value != null ? value.toString() : null;
    }

    /** Helper: read a String input with a default value. */
    protected String readStringInput(String name, String defaultValue) {
        String value = readStringInput(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    /** Helper: read a Boolean input with a default value. */
    protected Boolean readBooleanInput(String name, boolean defaultValue) {
        Object value = getInputParameter(name);
        return value != null ? (Boolean) value : defaultValue;
    }

    /** Helper: read an Integer input with a default value. */
    protected Integer readIntegerInput(String name, int defaultValue) {
        Object value = getInputParameter(name);
        return value != null ? ((Number) value).intValue() : defaultValue;
    }

    /**
     * Builds the common connection configuration from shared input parameters.
     * Subclasses call this in their buildConfiguration() to get a pre-filled builder.
     */
    protected DocuSignConfiguration.DocuSignConfigurationBuilder baseConfigurationBuilder() {
        return DocuSignConfiguration.builder()
                .integrationKey(resolveWithFallback(INPUT_INTEGRATION_KEY,
                        "docusign.integrationKey", "DOCUSIGN_INTEGRATION_KEY"))
                .rsaPrivateKey(resolveWithFallback(INPUT_RSA_PRIVATE_KEY,
                        "docusign.rsaPrivateKey", "DOCUSIGN_RSA_PRIVATE_KEY"))
                .userId(resolveWithFallback(INPUT_USER_ID,
                        "docusign.userId", "DOCUSIGN_USER_ID"))
                .accountId(resolveWithFallback(INPUT_ACCOUNT_ID,
                        "docusign.accountId", "DOCUSIGN_ACCOUNT_ID"))
                .basePath(readStringInput(INPUT_BASE_PATH, "https://www.docusign.net/restapi"))
                .oAuthBasePath(readStringInput(INPUT_OAUTH_BASE_PATH, "https://account.docusign.com"))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 60000));
    }

    /**
     * Resolves a parameter value from connector input, then system property, then env var.
     */
    private String resolveWithFallback(String inputName, String sysProp, String envVar) {
        String value = readStringInput(inputName);
        if (value == null || value.isBlank()) {
            value = System.getProperty(sysProp);
        }
        if (value == null || value.isBlank()) {
            value = System.getenv(envVar);
        }
        return value;
    }
}
