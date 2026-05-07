package com.interview.platform.repository;

import com.interview.platform.model.InterviewerAvailability;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewerAvailabilityRepository extends MongoRepository<InterviewerAvailability, String> {
    List<InterviewerAvailability> findByInterviewerIdOrderByDayOfWeekAscStartTimeAsc(String interviewerId);
    List<InterviewerAvailability> findByInterviewerIdAndDayOfWeekOrderByStartTimeAsc(String interviewerId, DayOfWeek dayOfWeek);
    Optional<InterviewerAvailability> findByIdAndInterviewerId(String id, String interviewerId);
}
