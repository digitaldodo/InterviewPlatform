package com.interview.platform.repository;

import com.interview.platform.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    @Query("{ '$or': [ { 'roles': ?0 }, { 'role': ?0 } ] }")
    List<User> findByRole(String role);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    @Query(value = "{ '$or': [ { 'roles': ?0 }, { 'role': ?0 } ] }", sort = "{ 'averageRating': -1 }")
    List<User> findByRoleOrderByAverageRatingDesc(String role);
    @Query(value = "{ 'acceptingBookings': ?1, '$or': [ { 'roles': ?0 }, { 'role': ?0 } ] }", sort = "{ 'completedInterviews': -1 }")
    List<User> findByRoleAndAcceptingBookingsOrderByCompletedInterviewsDesc(String role, Boolean acceptingBookings);
}
