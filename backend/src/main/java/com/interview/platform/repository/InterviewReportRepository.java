package com.interview.platform.repository;

import com.interview.platform.model.InterviewReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewReportRepository extends MongoRepository<InterviewReport, String> {
    Optional<InterviewReport> findBySessionId(String sessionId);
    List<InterviewReport> findByIntervieweeIdOrderByCreatedAtDesc(String intervieweeId);
    List<InterviewReport> findByInterviewerIdOrderByCreatedAtDesc(String interviewerId);
    void deleteBySessionId(String sessionId);
}
