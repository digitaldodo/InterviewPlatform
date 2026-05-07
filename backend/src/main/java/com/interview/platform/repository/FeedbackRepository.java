package com.interview.platform.repository;

import com.interview.platform.model.Feedback;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FeedbackRepository extends MongoRepository<Feedback, String> {
    List<Feedback> findBySessionId(String sessionId);
    void deleteBySessionId(String sessionId);
}
