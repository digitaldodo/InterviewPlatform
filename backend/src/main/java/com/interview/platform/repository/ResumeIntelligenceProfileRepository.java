package com.interview.platform.repository;

import com.interview.platform.model.ResumeIntelligenceProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResumeIntelligenceProfileRepository extends MongoRepository<ResumeIntelligenceProfile, String> {
    Optional<ResumeIntelligenceProfile> findByUserId(String userId);
}
