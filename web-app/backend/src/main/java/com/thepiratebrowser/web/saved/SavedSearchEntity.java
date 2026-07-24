package com.thepiratebrowser.web.saved;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_searches")
public class SavedSearchEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 250)
    private String query;

    @Column(name = "minimum_seeders", nullable = false)
    private int minimumSeeders;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "known_magnets", nullable = false, columnDefinition = "TEXT")
    private String knownMagnets;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavedSearchEntity() {
    }

    public SavedSearchEntity(
            UUID userId,
            String name,
            String query,
            int minimumSeeders,
            String knownMagnets
    ) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.query = query;
        this.minimumSeeders = minimumSeeders;
        this.enabled = true;
        this.knownMagnets = knownMagnets;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getQuery() {
        return query;
    }

    public int getMinimumSeeders() {
        return minimumSeeders;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getKnownMagnets() {
        return knownMagnets;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String name, String query, int minimumSeeders, boolean enabled) {
        this.name = name;
        this.query = query;
        this.minimumSeeders = minimumSeeders;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void checked(String knownMagnets, Instant checkedAt) {
        this.knownMagnets = knownMagnets;
        this.lastCheckedAt = checkedAt;
        this.updatedAt = checkedAt;
    }
}
