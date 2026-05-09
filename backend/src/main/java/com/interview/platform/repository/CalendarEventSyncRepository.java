package com.interview.platform.repository;

import com.interview.platform.model.CalendarEventSync;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalendarEventSyncRepository extends MongoRepository<CalendarEventSync, String> {
    Optional<CalendarEventSync> findByProviderAndUserIdAndSessionId(String provider, String userId, String sessionId);
    List<CalendarEventSync> findByProviderAndUserId(String provider, String userId);
    void deleteByProviderAndUserId(String provider, String userId);
}
