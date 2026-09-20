package com.huy.jobpulse.admin.application;

import com.huy.jobpulse.admin.domain.AdminAuditEntry;
import com.huy.jobpulse.admin.infrastructure.AdminAuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.UUID;

@Service
public class AdminAuditService {
    private final AdminAuditRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AdminAuditService(AdminAuditRepository repository, ObjectMapper mapper, Clock clock) {
        this.repository = repository; this.mapper = mapper; this.clock = clock;
    }

    public UUID record(HttpServletRequest request, String action, String targetType,
            Object targetId, Object before, Object after) {
        String actor = request.getHeader("X-Admin-Actor");
        if (actor == null || actor.isBlank()) actor = "local-operator";
        UUID correlation = correlation(request);
        repository.save(AdminAuditEntry.create(actor.strip(), action, targetType,
                String.valueOf(targetId), clock.instant(), json(before), json(after), correlation));
        return correlation;
    }

    public String actor(HttpServletRequest request) {
        String actor = request.getHeader("X-Admin-Actor");
        return actor == null || actor.isBlank() ? "local-operator" : actor.strip();
    }

    private UUID correlation(HttpServletRequest request) {
        String value = request.getHeader("X-Correlation-ID");
        if (value != null) try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) {}
        return UUID.randomUUID();
    }

    private String json(Object value) { return value == null ? null : mapper.writeValueAsString(value); }
}
