package com.thepiratebrowser.android;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CrashReporterTest {
    @Test
    public void removesOAuthTokensAndBearerCredentials() {
        String sanitized = CrashReporter.sanitize(
                "https://example.test/play?oauth_token=secret123&x=1 "
                        + "Authorization: Bearer abc.def-456"
        );

        assertFalse(sanitized.contains("secret123"));
        assertFalse(sanitized.contains("abc.def-456"));
        assertTrue(sanitized.contains("oauth_token=[REDACTED]"));
        assertTrue(sanitized.contains("Bearer [REDACTED]"));
    }

    @Test
    public void removesUrlEncodedOAuthTokens() {
        String sanitized = CrashReporter.sanitize(
                "oauth_token%3Dencoded-secret%26next%3Dvalue"
        );

        assertFalse(sanitized.contains("encoded-secret"));
        assertTrue(sanitized.contains("oauth_token%3D[REDACTED]"));
    }
}
