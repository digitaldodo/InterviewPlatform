package com.interview.platform.service;

import com.interview.platform.dto.AvailabilityDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.InterviewerAvailability;
import com.interview.platform.model.User;
import com.interview.platform.repository.InterviewerAvailabilityRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AvailabilityService {

    private static final Map<String, DayOfWeek> DAY_ALIASES = Map.ofEntries(
            Map.entry("MON", DayOfWeek.MONDAY),
            Map.entry("MONDAY", DayOfWeek.MONDAY),
            Map.entry("TUE", DayOfWeek.TUESDAY),
            Map.entry("TUESDAY", DayOfWeek.TUESDAY),
            Map.entry("WED", DayOfWeek.WEDNESDAY),
            Map.entry("WEDNESDAY", DayOfWeek.WEDNESDAY),
            Map.entry("THU", DayOfWeek.THURSDAY),
            Map.entry("THURSDAY", DayOfWeek.THURSDAY),
            Map.entry("FRI", DayOfWeek.FRIDAY),
            Map.entry("FRIDAY", DayOfWeek.FRIDAY),
            Map.entry("SAT", DayOfWeek.SATURDAY),
            Map.entry("SATURDAY", DayOfWeek.SATURDAY),
            Map.entry("SUN", DayOfWeek.SUNDAY),
            Map.entry("SUNDAY", DayOfWeek.SUNDAY)
    );

    private final InterviewerAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;

    public AvailabilityService(InterviewerAvailabilityRepository availabilityRepository, UserRepository userRepository) {
        this.availabilityRepository = availabilityRepository;
        this.userRepository = userRepository;
    }

    public List<AvailabilityDtos.AvailabilityResponse> getOwnAvailability(String interviewerId) {
        User interviewer = requireInterviewer(interviewerId);
        return availabilityRepository.findByInterviewerIdOrderByDayOfWeekAscStartTimeAsc(interviewer.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public AvailabilityDtos.AvailabilityResponse create(String interviewerId, AvailabilityDtos.UpsertRequest request) {
        User interviewer = requireInterviewer(interviewerId);
        DayOfWeek dayOfWeek = parseDayOfWeek(request.getDayOfWeek());
        LocalTime startTime = LocalTime.parse(request.getStartTime());
        LocalTime endTime = LocalTime.parse(request.getEndTime());
        validateWindow(interviewer.getId(), null, dayOfWeek, startTime, endTime, request.getDurationMinutes());

        InterviewerAvailability availability = new InterviewerAvailability();
        availability.setInterviewerId(interviewer.getId());
        availability.setDayOfWeek(dayOfWeek);
        availability.setStartTime(startTime.truncatedTo(ChronoUnit.MINUTES).toString());
        availability.setEndTime(endTime.truncatedTo(ChronoUnit.MINUTES).toString());
        availability.setDurationMinutes(request.getDurationMinutes());
        availability.setCreatedAt(Instant.now());
        availability.setUpdatedAt(Instant.now());
        return toResponse(availabilityRepository.save(availability));
    }

    public AvailabilityDtos.AvailabilityResponse update(String interviewerId, String availabilityId, AvailabilityDtos.UpsertRequest request) {
        User interviewer = requireInterviewer(interviewerId);
        InterviewerAvailability availability = availabilityRepository.findByIdAndInterviewerId(availabilityId, interviewer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Availability not found"));

        DayOfWeek dayOfWeek = parseDayOfWeek(request.getDayOfWeek());
        LocalTime startTime = LocalTime.parse(request.getStartTime());
        LocalTime endTime = LocalTime.parse(request.getEndTime());
        validateWindow(interviewer.getId(), availability.getId(), dayOfWeek, startTime, endTime, request.getDurationMinutes());

        availability.setDayOfWeek(dayOfWeek);
        availability.setStartTime(startTime.truncatedTo(ChronoUnit.MINUTES).toString());
        availability.setEndTime(endTime.truncatedTo(ChronoUnit.MINUTES).toString());
        availability.setDurationMinutes(request.getDurationMinutes());
        availability.setUpdatedAt(Instant.now());
        return toResponse(availabilityRepository.save(availability));
    }

    public void delete(String interviewerId, String availabilityId) {
        User interviewer = requireInterviewer(interviewerId);
        InterviewerAvailability availability = availabilityRepository.findByIdAndInterviewerId(availabilityId, interviewer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Availability not found"));
        availabilityRepository.delete(availability);
    }

    private User requireInterviewer(String interviewerId) {
        User user = userRepository.findById(interviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));
        if (!user.hasRole("INTERVIEWER")) {
            throw new IllegalArgumentException("Only interviewers can manage availability");
        }
        return user;
    }

    private void validateWindow(String interviewerId, String availabilityId, DayOfWeek dayOfWeek,
                                LocalTime startTime, LocalTime endTime, Integer durationMinutes) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        long windowMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new IllegalArgumentException("Duration must be greater than 0");
        }
        if (durationMinutes > windowMinutes) {
            throw new IllegalArgumentException("Duration cannot be longer than the availability window");
        }

        List<InterviewerAvailability> sameDay = availabilityRepository.findByInterviewerIdAndDayOfWeekOrderByStartTimeAsc(interviewerId, dayOfWeek);
        for (InterviewerAvailability existing : sameDay) {
            if (availabilityId != null && availabilityId.equals(existing.getId())) {
                continue;
            }
            LocalTime existingStart = LocalTime.parse(existing.getStartTime());
            LocalTime existingEnd = LocalTime.parse(existing.getEndTime());
            boolean overlaps = startTime.isBefore(existingEnd) && endTime.isAfter(existingStart);
            if (overlaps) {
                throw new IllegalArgumentException("Availability overlaps an existing schedule");
            }
        }
    }

    private DayOfWeek parseDayOfWeek(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Day of week is required");
        }
        DayOfWeek value = DAY_ALIASES.get(rawValue.trim().toUpperCase(Locale.ROOT));
        if (value == null) {
            throw new IllegalArgumentException("Day of week must be Monday through Sunday");
        }
        return value;
    }

    private AvailabilityDtos.AvailabilityResponse toResponse(InterviewerAvailability availability) {
        return new AvailabilityDtos.AvailabilityResponse(
                availability.getId(),
                availability.getInterviewerId(),
                availability.getDayOfWeek() == null ? null : availability.getDayOfWeek().name(),
                availability.getStartTime(),
                availability.getEndTime(),
                availability.getDurationMinutes(),
                availability.getCreatedAt(),
                availability.getUpdatedAt()
        );
    }
}
