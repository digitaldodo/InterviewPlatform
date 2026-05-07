package com.interview.platform.service;

import com.interview.platform.model.ModerationAuditLog;
import com.interview.platform.repository.ModerationAuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class ModerationAuditService {
    private final ModerationAuditLogRepository moderationAuditLogRepository;

    public ModerationAuditService(ModerationAuditLogRepository moderationAuditLogRepository) {
        this.moderationAuditLogRepository = moderationAuditLogRepository;
    }

    public void log(String entityType,
                    String entityId,
                    String actorUserId,
                    String subjectUserId,
                    String action,
                    String reason,
                    String summary,
                    Map<String, Object> beforeState,
                    Map<String, Object> afterState) {
        ModerationAuditLog log = new ModerationAuditLog();
        log.setEntityType(normalize(entityType));
        log.setEntityId(entityId);
        log.setActorUserId(actorUserId);
        log.setSubjectUserId(subjectUserId);
        log.setAction(normalize(action));
        log.setReason(trimToNull(reason));
        log.setSummary(trimToNull(summary));
        log.setBeforeState(copy(beforeState));
        log.setAfterState(copy(afterState));
        log.setCreatedAt(Instant.now());
        moderationAuditLogRepository.save(log);
    }

    private Map<String, Object> copy(Map<String, Object> input) {
        return input == null ? new HashMap<>() : new HashMap<>(input);
    }

    private String normalize(String value) {
        return trimToNull(value) == null ? null : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
