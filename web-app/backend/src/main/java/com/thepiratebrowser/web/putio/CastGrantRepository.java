package com.thepiratebrowser.web.putio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.time.Instant;

public interface CastGrantRepository extends JpaRepository<CastGrant, UUID> {
    long deleteByExpiresAtBefore(Instant cutoff);
}
