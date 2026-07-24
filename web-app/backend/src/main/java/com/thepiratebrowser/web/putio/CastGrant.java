package com.thepiratebrowser.web.putio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cast_grants")
public class CastGrant {
    @Id
    private UUID token;

    @Column(name = "file_id", nullable = false)
    private long fileId;

    @Column(name = "created_by", nullable = false, length = 40)
    private String createdBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CastGrant() {
    }

    CastGrant(long fileId, String createdBy, Instant expiresAt) {
        this.token = UUID.randomUUID();
        this.fileId = fileId;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public UUID getToken() {
        return token;
    }

    public long getFileId() {
        return fileId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
