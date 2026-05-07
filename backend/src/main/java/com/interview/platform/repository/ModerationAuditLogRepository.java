package com.interview.platform.repository;

import com.interview.platform.model.ModerationAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModerationAuditLogRepository extends MongoRepository<ModerationAuditLog, String> {
    Page<ModerationAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<ModerationAuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);
    Page<ModerationAuditLog> findBySubjectUserIdOrderByCreatedAtDesc(String subjectUserId, Pageable pageable);
    List<ModerationAuditLog> findTop20ByOrderByCreatedAtDesc();
}
