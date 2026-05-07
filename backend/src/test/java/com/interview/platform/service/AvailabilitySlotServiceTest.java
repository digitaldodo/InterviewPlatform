package com.interview.platform.service;

import com.interview.platform.dto.AvailabilityDtos;
import com.interview.platform.model.InterviewerAvailability;
import com.interview.platform.model.Session;
import com.interview.platform.repository.InterviewerAvailabilityRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilitySlotServiceTest {

    @Mock
    private InterviewerAvailabilityRepository availabilityRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    void generatedSlotsExcludeBookedWindows() {
        SchedulingTimeService timeService = new SchedulingTimeService(
                ZoneId.of("UTC"),
                Clock.fixed(Instant.parse("2026-05-11T08:00:00Z"), ZoneOffset.UTC)
        );
        AvailabilitySlotService slotService = new AvailabilitySlotService(
                availabilityRepository,
                sessionRepository,
                userRepository,
                timeService,
                2
        );

        InterviewerAvailability availability = new InterviewerAvailability();
        availability.setId("av-1");
        availability.setInterviewerId("int-1");
        availability.setDayOfWeek(DayOfWeek.MONDAY);
        availability.setStartTime("10:00");
        availability.setEndTime("13:00");
        availability.setDurationMinutes(60);

        Session booked = new Session();
        booked.setInterviewerId("int-1");
        booked.setStartTime("2026-05-11T11:00:00Z");
        booked.setDurationMinutes(60);
        booked.setStatus("PENDING");

        when(availabilityRepository.findByInterviewerIdOrderByDayOfWeekAscStartTimeAsc("int-1"))
                .thenReturn(List.of(availability));
        when(sessionRepository.findByInterviewerIdAndStatusIn("int-1", List.of("PENDING", "CONFIRMED")))
                .thenReturn(List.of(booked));

        List<AvailabilityDtos.GeneratedSlotResponse> slots = slotService.generatedSlotResponses("int-1", 2);

        assertEquals(2, slots.size());
        assertEquals("2026-05-11T10:00:00Z", slots.get(0).getStartTime());
        assertEquals("2026-05-11T12:00:00Z", slots.get(1).getStartTime());
    }

    @Test
    void resolveRequestedBookingRejectsPastTimeWithoutStructuredAvailability() {
        SchedulingTimeService timeService = new SchedulingTimeService(
                ZoneId.of("UTC"),
                Clock.fixed(Instant.parse("2026-05-11T08:00:00Z"), ZoneOffset.UTC)
        );
        AvailabilitySlotService slotService = new AvailabilitySlotService(
                availabilityRepository,
                sessionRepository,
                userRepository,
                timeService,
                14
        );

        when(availabilityRepository.findByInterviewerIdOrderByDayOfWeekAscStartTimeAsc("int-1"))
                .thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> slotService.resolveRequestedBooking("int-1", "2026-05-11T07:30:00Z", 60));
    }
}
