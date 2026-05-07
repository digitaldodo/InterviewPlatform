package com.interview.platform.repository;

import com.interview.platform.model.VerificationOtp;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface VerificationOtpRepository extends MongoRepository<VerificationOtp, String> {
    Optional<VerificationOtp> findTopByEmailOrderByCreatedAtDesc(String email);
    void deleteByEmail(String email);
}
