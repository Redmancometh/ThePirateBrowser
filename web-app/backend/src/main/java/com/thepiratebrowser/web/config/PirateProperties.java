package com.thepiratebrowser.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pirate")
public record PirateProperties(
        String putioToken,
        String registrationInviteCode,
        String adminUsername,
        String adminPassword,
        String buildCanary
) {
    public PirateProperties {
        putioToken = safe(putioToken);
        registrationInviteCode = safe(registrationInviteCode);
        adminUsername = safe(adminUsername);
        adminPassword = safe(adminPassword);
        buildCanary = safe(buildCanary).isEmpty() ? "local" : safe(buildCanary);
    }

    public boolean putIoConfigured() {
        return !putioToken.isBlank();
    }

    public boolean registrationEnabled() {
        return !registrationInviteCode.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
