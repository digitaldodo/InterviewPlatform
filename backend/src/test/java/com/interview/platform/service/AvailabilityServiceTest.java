package com.interview.platform.service;

import com.interview.platform.dto.AvailabilityDtos;
import com.interview.platform.model.InterviewerAvailability;
import com.interview.platform.model.User;
import com.interview.platform.repository.InterviewerAvailabilityRepository;
import com.interview.platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private InterviewerAvailabilityRepository availabilityRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private AvailabilityService availabilityService;

    @Test
    void createRejectsOverlappingAvailability() {
        User interviewer = interviewer("int-1");
        InterviewerAvailability existing = new InterviewerAvailability();
        existing.setId("existing");
        existing.setInterviewerId("int-1");
        existing.setDayOfWeek(DayOfWeek.MONDAY);
        existing.setStartTime("10:00");
        existing.setEndTime("12:00");
        existing.setDurationMinutes(60);

        AvailabilityDtos.UpsertRequest request = new AvailabilityDtos.UpsertRequest();
        request.setDayOfWeek("MONDAY");
        request.setStartTime("11:00");
        request.setEndTime("13:00");
        request.setDurationMinutes(60);

        when(userRepository.findById("int-1")).thenReturn(Optional.of(interviewer));
        when(availabilityRepository.findByInterviewerIdAndDayOfWeekOrderByStartTimeAsc("int-1", DayOfWeek.MONDAY))
                .thenReturn(List.of(existing));

        assertThrows(IllegalArgumentException.class, () -> availabilityService.create("int-1", request));
    }

    private User interviewer(String id) {
        User user = new User();
        user.setId(id);
        user.setRole("INTERVIEWER");
        return user;
    }
}
