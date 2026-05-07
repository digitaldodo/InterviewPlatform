package com.interview.platform.repository;

import com.interview.platform.model.Session;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends MongoRepository<Session, String> {
    List<Session> findByInterviewerId(String interviewerId);
    List<Session> findByCandidateId(String candidateId);
    List<Session> findByInterviewerIdOrCandidateId(String interviewerId, String candidateId);
    List<Session> findByCandidateIdAndStatusIn(String candidateId, List<String> statuses);
    List<Session> findByInterviewerIdAndStatusIn(String interviewerId, List<String> statuses);
    List<Session> findByStatusAndPreInterviewReminderSentAtIsNull(String status, Pageable pageable);
    boolean existsByInterviewerIdAndStartTimeAndStatusIn(String interviewerId, String startTime, List<String> statuses);
}
