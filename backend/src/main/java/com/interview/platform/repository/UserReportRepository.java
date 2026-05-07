package com.interview.platform.repository;

import com.interview.platform.model.UserReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserReportRepository extends MongoRepository<UserReport, String> {
    boolean existsByReporterIdAndReportedUserIdAndSessionId(String reporterId, String reportedUserId, String sessionId);
    List<UserReport> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}
