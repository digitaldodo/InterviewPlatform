package com.interview.platform.repository;

import com.interview.platform.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    // Used by SessionService to find interviewers for auto-matching
    List<User> findByRole(String role);
}
