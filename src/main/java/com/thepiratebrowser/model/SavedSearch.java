package com.thepiratebrowser.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class SavedSearch {
    private String id = UUID.randomUUID().toString();
    private String name = "";
    private String query = "";
    private int minimumSeeders;
    private boolean enabled = true;
    private long revision;
    private Instant lastChecked;
    private Set<String> seenResultIds = new LinkedHashSet<>();

    public SavedSearch() {
    }

    public SavedSearch(String name, String query, int minimumSeeders) {
        this.name = name;
        this.query = query;
        this.minimumSeeders = minimumSeeders;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public int getMinimumSeeders() { return minimumSeeders; }
    public void setMinimumSeeders(int minimumSeeders) { this.minimumSeeders = minimumSeeders; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = Math.max(0, revision); }
    public Instant getLastChecked() { return lastChecked; }
    public void setLastChecked(Instant lastChecked) { this.lastChecked = lastChecked; }
    public Set<String> getSeenResultIds() { return seenResultIds; }
    public void setSeenResultIds(Set<String> seenResultIds) {
        this.seenResultIds = seenResultIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(seenResultIds);
    }

    @Override
    public String toString() {
        return name == null || name.isBlank() ? query : name;
    }
}
