package com.bonitasoft.connectors.docusign;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class RetryPolicyTest {

    @Test
    void should_succeed_on_first_attempt() throws DocuSignException {
        var policy = new RetryPolicy(3);
        String result = policy.execute(() -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void should_retry_on_retryable_exception() throws DocuSignException {
        var policy = new TestRetryPolicy(3);
        var attempts = new AtomicInteger(0);

        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new DocuSignException("Rate limited", 429, true);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void should_fail_immediately_on_non_retryable_exception() {
        var policy = new TestRetryPolicy(3);

        assertThatThrownBy(() -> policy.execute(() -> {
            throw new DocuSignException("Bad request", 400, false);
        })).isInstanceOf(DocuSignException.class)
                .hasMessageContaining("Bad request");
    }

    @Test
    void should_fail_after_max_retries() {
        var policy = new TestRetryPolicy(2);

        assertThatThrownBy(() -> policy.execute(() -> {
            throw new DocuSignException("Server error", 500, true);
        })).isInstanceOf(DocuSignException.class)
                .hasMessageContaining("Server error");
    }

    @Test
    void should_identify_retryable_status_codes() {
        assertThat(RetryPolicy.isRetryableStatusCode(429)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(500)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(502)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(503)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(400)).isFalse();
        assertThat(RetryPolicy.isRetryableStatusCode(401)).isFalse();
        assertThat(RetryPolicy.isRetryableStatusCode(404)).isFalse();
    }

    /** Test subclass that skips actual sleeping */
    private static class TestRetryPolicy extends RetryPolicy {
        TestRetryPolicy(int maxRetries) {
            super(maxRetries);
        }

        @Override
        void sleep(long millis) {
            // no-op for tests
        }
    }
}
