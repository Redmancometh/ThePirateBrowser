package com.thepiratebrowser.web.audit;

import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public void record(
            String username,
            String action,
            String targetType,
            Object targetId,
            String detail
    ) {
        repository.save(new AuditEvent(
                username,
                action,
                targetType,
                targetId == null ? null : String.valueOf(targetId),
                detail == null ? null : detail.substring(0, Math.min(detail.length(), 500))
        ));
    }
}
