package com.interview.platform.repository;

import com.interview.platform.model.PrepModule;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrepModuleRepository extends MongoRepository<PrepModule, String> {
    List<PrepModule> findByVisibilityStatusOrderByUpdatedAtDesc(String visibilityStatus);
}
