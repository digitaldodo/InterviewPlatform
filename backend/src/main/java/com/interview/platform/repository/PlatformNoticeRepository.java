package com.interview.platform.repository;

import com.interview.platform.model.PlatformNotice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PlatformNoticeRepository extends MongoRepository<PlatformNotice, String> {
    List<PlatformNotice> findAllByOrderByUpdatedAtDesc();
}
