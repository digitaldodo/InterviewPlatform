package com.interview.platform.repository;

import com.interview.platform.model.Feedback;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface FeedbackRepository extends MongoRepository<Feedback, String> {
    List<Feedback> findBySessionId(String sessionId);
    List<Feedback> findBySessionIdIn(List<String> sessionIds);
    List<Feedback> findByInterviewerIdAndPublicReviewTrueOrderByCreatedAtDesc(String interviewerId);
    List<Feedback> findByInterviewerIdAndPublicReviewTrue(String interviewerId);
    List<Feedback> findByReviewTypeOrderByCreatedAtDesc(String reviewType);
    List<Feedback> findTop10ByReviewerIdOrderByCreatedAtDesc(String reviewerId);
    Feedback findTopBySessionIdAndReviewerIdOrderByCreatedAtDesc(String sessionId, String reviewerId);
    List<Feedback> findByReviewerIdAndCreatedAtAfterOrderByCreatedAtDesc(String reviewerId, Instant createdAt);
    long countByReviewerIdAndCreatedAtAfter(String reviewerId, Instant createdAt);
    long countByFlaggedForModerationTrue();
    List<Feedback> findByFlaggedForModerationTrueOrderByCreatedAtDesc();
    boolean existsBySessionIdAndReviewerId(String sessionId, String reviewerId);
    void deleteBySessionId(String sessionId);
}
