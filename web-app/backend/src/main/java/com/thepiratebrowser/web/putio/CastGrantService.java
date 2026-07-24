package com.thepiratebrowser.web.putio;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class CastGrantService {
    private static final Duration LIFETIME = Duration.ofHours(8);
    private final CastGrantRepository repository;

    public CastGrantService(CastGrantRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CastGrant create(long fileId, String username) {
        if (fileId <= 0) {
            throw new IllegalArgumentException("A valid file is required.");
        }
        Instant now = Instant.now();
        repository.deleteByExpiresAtBefore(now);
        return repository.save(new CastGrant(fileId, username, now.plus(LIFETIME)));
    }

    @Transactional
    public long requireFile(UUID token) {
        CastGrant grant = repository.findById(token)
                .orElseThrow(() -> new InvalidCastGrantException("Cast link not found."));
        if (!grant.getExpiresAt().isAfter(Instant.now())) {
            repository.delete(grant);
            throw new InvalidCastGrantException("Cast link expired.");
        }
        return grant.getFileId();
    }

    public static final class InvalidCastGrantException extends RuntimeException {
        InvalidCastGrantException(String message) {
            super(message);
        }
    }
}
