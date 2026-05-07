package com.interview.platform.repository;

import com.interview.platform.model.Feedback;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FeedbackRepository extends MongoRepository<Feedback, String> {
    List<Feedback> findBySessionId(String sessionId);
    List<Feedback> findBySessionIdIn(List<String> sessionIds);
    List<Feedback> findByInterviewerIdAndPublicReviewTrueOrderByCreatedAtDesc(String interviewerId);
    List<Feedback> findByReviewTypeOrderByCreatedAtDesc(String reviewType);
    boolean existsBySessionIdAndReviewerId(String sessionId, String reviewerId);
    void deleteBySessionId(String sessionId);
}
