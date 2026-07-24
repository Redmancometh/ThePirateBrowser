package com.thepiratebrowser.model;

import java.util.ArrayList;
import java.util.List;

public class LocalSettings {
    private String putIoClientSecret = "";
    private String putIoClientId = "";
    private String putIoToken = "";
    private String pirateBayApiBaseUrl = "https://apibay.org";
    private int monitorIntervalMinutes = 15;
    private int defaultMinimumSeeders;
    private boolean putIoAutoRefreshEnabled = true;
    private int putIoRefreshIntervalSeconds = 3;
    private boolean savedSearchesPanelVisible = true;
    private boolean putIoPanelVisible = true;
    private double savedSearchesPanelRatio = 0.19;
    private double putIoPanelRatio = 0.21;
    private List<String> enabledTorrentSources =
            new ArrayList<>(List.of(
                    "pirate-bay", "knaben", "magnetz", "torrents-csv", "nyaa", "eztv", "yts"));
    private List<SavedSearch> savedSearches = new ArrayList<>();

    public String getPutIoClientSecret() { return putIoClientSecret; }
    public void setPutIoClientSecret(String putIoClientSecret) {
        this.putIoClientSecret = putIoClientSecret == null ? "" : putIoClientSecret;
    }
    public String getPutIoClientId() { return putIoClientId; }
    public void setPutIoClientId(String putIoClientId) {
        this.putIoClientId = putIoClientId == null ? "" : putIoClientId;
    }
    public String getPutIoToken() { return putIoToken; }
    public void setPutIoToken(String putIoToken) { this.putIoToken = putIoToken == null ? "" : putIoToken; }
    public String getPirateBayApiBaseUrl() { return pirateBayApiBaseUrl; }
    public void setPirateBayApiBaseUrl(String pirateBayApiBaseUrl) { this.pirateBayApiBaseUrl = pirateBayApiBaseUrl; }
    public int getMonitorIntervalMinutes() { return monitorIntervalMinutes; }
    public void setMonitorIntervalMinutes(int monitorIntervalMinutes) {
        this.monitorIntervalMinutes = Math.max(1, monitorIntervalMinutes);
    }
    public int getDefaultMinimumSeeders() { return defaultMinimumSeeders; }
    public void setDefaultMinimumSeeders(int defaultMinimumSeeders) {
        this.defaultMinimumSeeders = Math.max(0, defaultMinimumSeeders);
    }
    public boolean isPutIoAutoRefreshEnabled() { return putIoAutoRefreshEnabled; }
    public void setPutIoAutoRefreshEnabled(boolean putIoAutoRefreshEnabled) {
        this.putIoAutoRefreshEnabled = putIoAutoRefreshEnabled;
    }
    public int getPutIoRefreshIntervalSeconds() { return putIoRefreshIntervalSeconds; }
    public void setPutIoRefreshIntervalSeconds(int putIoRefreshIntervalSeconds) {
        this.putIoRefreshIntervalSeconds = Math.max(1, putIoRefreshIntervalSeconds);
    }
    public boolean isSavedSearchesPanelVisible() { return savedSearchesPanelVisible; }
    public void setSavedSearchesPanelVisible(boolean savedSearchesPanelVisible) {
        this.savedSearchesPanelVisible = savedSearchesPanelVisible;
    }
    public boolean isPutIoPanelVisible() { return putIoPanelVisible; }
    public void setPutIoPanelVisible(boolean putIoPanelVisible) {
        this.putIoPanelVisible = putIoPanelVisible;
    }
    public double getSavedSearchesPanelRatio() { return savedSearchesPanelRatio; }
    public void setSavedSearchesPanelRatio(double savedSearchesPanelRatio) {
        this.savedSearchesPanelRatio = clampPanelRatio(savedSearchesPanelRatio);
    }
    public double getPutIoPanelRatio() { return putIoPanelRatio; }
    public void setPutIoPanelRatio(double putIoPanelRatio) {
        this.putIoPanelRatio = clampPanelRatio(putIoPanelRatio);
    }
    public List<String> getEnabledTorrentSources() { return enabledTorrentSources; }
    public void setEnabledTorrentSources(List<String> enabledTorrentSources) {
        this.enabledTorrentSources = enabledTorrentSources == null
                ? new ArrayList<>()
                : new ArrayList<>(enabledTorrentSources);
    }
    public List<SavedSearch> getSavedSearches() { return savedSearches; }
    public void setSavedSearches(List<SavedSearch> savedSearches) {
        this.savedSearches = savedSearches == null ? new ArrayList<>() : new ArrayList<>(savedSearches);
    }

    private static double clampPanelRatio(double ratio) {
        if (!Double.isFinite(ratio)) {
            return 0.2;
        }
        return Math.max(0.1, Math.min(0.4, ratio));
    }
}
