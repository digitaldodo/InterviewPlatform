package com.interview.platform.repository;

import com.interview.platform.model.FeedbackDraft;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeedbackDraftRepository extends MongoRepository<FeedbackDraft, String> {
    Optional<FeedbackDraft> findBySessionIdAndInterviewerId(String sessionId, String interviewerId);
    void deleteBySessionId(String sessionId);
}
