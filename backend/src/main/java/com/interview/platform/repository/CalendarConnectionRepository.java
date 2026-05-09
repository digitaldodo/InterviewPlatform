package com.interview.platform.repository;

import com.interview.platform.model.CalendarConnection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CalendarConnectionRepository extends MongoRepository<CalendarConnection, String> {
    Optional<CalendarConnection> findByProviderAndUserId(String provider, String userId);
    void deleteByProviderAndUserId(String provider, String userId);
}
