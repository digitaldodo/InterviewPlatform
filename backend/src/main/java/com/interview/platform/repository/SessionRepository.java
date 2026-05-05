package com.interview.platform.repository;

import com.interview.platform.model.Session;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends MongoRepository<Session, String> {
    List<Session> findByInterviewerId(String interviewerId);
    List<Session> findByCandidateId(String candidateId);
}
