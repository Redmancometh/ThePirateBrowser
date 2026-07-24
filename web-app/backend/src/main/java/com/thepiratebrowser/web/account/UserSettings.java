package com.thepiratebrowser.web.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "user_settings")
public class UserSettings {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "enabled_sources", nullable = false, length = 512)
    private String enabledSources;

    protected UserSettings() {
    }

    public UserSettings(UUID userId, String enabledSources) {
        this.userId = userId;
        this.enabledSources = enabledSources;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEnabledSources() {
        return enabledSources;
    }

    public void setEnabledSources(String enabledSources) {
        this.enabledSources = enabledSources;
    }
}
