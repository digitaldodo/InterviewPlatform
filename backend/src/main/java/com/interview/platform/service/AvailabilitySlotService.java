package com.interview.platform.service;

import com.interview.platform.dto.AvailabilityDtos;
import com.interview.platform.model.InterviewerAvailability;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.InterviewerAvailabilityRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AvailabilitySlotService {

    public record GeneratedSlot(String availabilityId, String startTime, String endTime, int durationMinutes) {}
    public record BookingResolution(String startTime, int durationMinutes) {}

    private static final List<String> ACTIVE_SESSION_STATUSES = List.of("PENDING", "CONFIRMED");
    private static final int LEGACY_DURATION_MINUTES = 45;

    private final InterviewerAvailabilityRepository availabilityRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SchedulingTimeService schedulingTimeService;
    private final int defaultHorizonDays;

    public AvailabilitySlotService(InterviewerAvailabilityRepository availabilityRepository,
                                   SessionRepository sessionRepository,
                                   UserRepository userRepository,
                                   SchedulingTimeService schedulingTimeService,
                                   @Value("${app.scheduling.default-horizon-days:14}") int defaultHorizonDays) {
        this.availabilityRepository = availabilityRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.schedulingTimeService = schedulingTimeService;
        this.defaultHorizonDays = Math.max(1, defaultHorizonDays);
    }

    public List<String> availableSlotStartTimes(String interviewerId, Integer days) {
        return generatedSlots(interviewerId, days).stream()
                .map(GeneratedSlot::startTime)
                .toList();
    }

    public List<AvailabilityDtos.GeneratedSlotResponse> generatedSlotResponses(String interviewerId, Integer days) {
        return generatedSlots(interviewerId, days).stream()
                .map(slot -> new AvailabilityDtos.GeneratedSlotResponse(
                        slot.availabilityId(),
                        slot.startTime(),
                        slot.endTime(),
                        slot.durationMinutes()))
                .toList();
    }

    public BookingResolution resolveRequestedBooking(String interviewerId, String startTime, Integer requestedDurationMinutes) {
        List<InterviewerAvailability> schedules = availabilityRepository.findByInterviewerIdOrderByDayOfWeekAscStartTimeAsc(interviewerId);
        if (!schedules.isEmpty()) {
            OffsetDateTime requested = schedulingTimeService.parseStartTime(startTime);
            if (!requested.toInstant().isAfter(schedulingTimeService.nowInstant())) {
                throw new IllegalArgumentException("Past time slots cannot be booked");
            }
            GeneratedSlot slot = findStructuredSlot(interviewerId, requested)
                    .orElseThrow(() -> new IllegalArgumentException("That slot is no longer available"));
            boolean legacyDefaultFromOlderClients = requestedDurationMinutes != null && requestedDurationMinutes == LEGACY_DURATION_MINUTES;
            if (requestedDurationMinutes != null && !legacyDefaultFromOlderClients && requestedDurationMinutes != slot.durationMinutes()) {
                throw new IllegalArgumentException("Requested duration does not match interviewer availability");
            }
            return new BookingResolution(slot.startTime(), slot.durationMinutes());
        }

        Optional<OffsetDateTime> parsed = schedulingTimeService.tryParseStartTime(startTime);
        if (parsed.isPresent() && !parsed.get().toInstant().isAfter(schedulingTimeService.nowInstant())) {
            throw new IllegalArgumentException("Past time slots cannot be booked");
        }
        int durationMinutes = requestedDurationMinutes == null ? LEGACY_DURATION_MINUTES : requestedDurationMinutes;
        return new BookingResolution(startTime == null ? null : startTime.trim(), durationMinutes);
    }

    public boolean hasStructuredAvailability(String interviewerId) {
        return !availabilityRepository.findByInterviewerIdOrderByDayOfWeekAscStartTimeAsc(interviewerId).isEmpty();
    }

    private List<GeneratedSlot> generatedSlots(String interviewerId, Integer days) {
        List<InterviewerAvailability> schedules = availabilityRepository.findByInterviewerIdOrderByDayOfWeekAscStartTimeAsc(interviewerId);
        if (schedules.isEmpty()) {
            return legacySlots(interviewerId);
        }

        int horizonDays = days == null ? defaultHorizonDays : Math.max(1, days);
        LocalDate today = LocalDate.now(schedulingTimeService.getClock().withZone(schedulingTimeService.getZoneId()));
        Map<String, GeneratedSlot> slots = new LinkedHashMap<>();
        for (LocalDate date = today; date.isBefore(today.plusDays(horizonDays)); date = date.plusDays(1)) {
            for (InterviewerAvailability availability : schedules) {
                if (availability.getDayOfWeek() != date.getDayOfWeek()) {
                    continue;
                }
                appendSlotsForWindow(slots, availability, date);
            }
        }
        return removeBookedSlots(interviewerId, new ArrayList<>(slots.values())).stream()
                .sorted(Comparator.comparing(this::slotSortValue))
                .toList();
    }

    private Optional<GeneratedSlot> findStructuredSlot(String interviewerId, OffsetDateTime requested) {
        LocalDate requestedDate = requested.atZoneSameInstant(schedulingTimeService.getZoneId()).toLocalDate();
        DayOfWeek dayOfWeek = requestedDate.getDayOfWeek();
        List<InterviewerAvailability> schedules = availabilityRepository.findByInterviewerIdAndDayOfWeekOrderByStartTimeAsc(interviewerId, dayOfWeek);
        if (schedules.isEmpty()) {
            return Optional.empty();
        }
        Map<String, GeneratedSlot> slots = new LinkedHashMap<>();
        for (InterviewerAvailability availability : schedules) {
            appendSlotsForWindow(slots, availability, requestedDate);
        }
        Instant requestedInstant = requested.toInstant();
        return removeBookedSlots(interviewerId, new ArrayList<>(slots.values())).stream()
                .filter(slot -> schedulingTimeService.parseStartTime(slot.startTime()).toInstant().equals(requestedInstant))
                .findFirst();
    }

    private void appendSlotsForWindow(Map<String, GeneratedSlot> slots, InterviewerAvailability availability, LocalDate date) {
        LocalTime windowStart = LocalTime.parse(availability.getStartTime());
        LocalTime windowEnd = LocalTime.parse(availability.getEndTime());
        ZonedDateTime slotStart = ZonedDateTime.of(date, windowStart, schedulingTimeService.getZoneId());
        ZonedDateTime endBoundary = ZonedDateTime.of(date, windowEnd, schedulingTimeService.getZoneId());
        while (!slotStart.plusMinutes(availability.getDurationMinutes()).isAfter(endBoundary)) {
            if (slotStart.toInstant().isAfter(schedulingTimeService.nowInstant())) {
                ZonedDateTime slotEnd = slotStart.plusMinutes(availability.getDurationMinutes());
                GeneratedSlot slot = new GeneratedSlot(
                        availability.getId(),
                        schedulingTimeService.format(slotStart),
                        schedulingTimeService.format(slotEnd),
                        availability.getDurationMinutes()
                );
                slots.putIfAbsent(slot.startTime(), slot);
            }
            slotStart = slotStart.plusMinutes(availability.getDurationMinutes());
        }
    }

    private List<GeneratedSlot> removeBookedSlots(String interviewerId, List<GeneratedSlot> slots) {
        List<Session> activeSessions = sessionRepository.findByInterviewerIdAndStatusIn(interviewerId, ACTIVE_SESSION_STATUSES);
        return slots.stream()
                .filter(slot -> activeSessions.stream().noneMatch(session -> conflicts(session, slot)))
                .toList();
    }

    private boolean conflicts(Session session, GeneratedSlot slot) {
        if (session.getStartTime() == null || session.getStartTime().isBlank()) {
            return false;
        }
        if (session.getStartTime().equals(slot.startTime())) {
            return true;
        }
        try {
            Instant sessionStart = schedulingTimeService.parseStartTime(session.getStartTime()).toInstant();
            Instant sessionEnd = sessionStart.plusSeconds((long) effectiveSessionDurationMinutes(session) * 60);
            Instant slotStart = schedulingTimeService.parseStartTime(slot.startTime()).toInstant();
            Instant slotEnd = schedulingTimeService.parseStartTime(slot.endTime()).toInstant();
            return slotStart.isBefore(sessionEnd) && slotEnd.isAfter(sessionStart);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private List<GeneratedSlot> legacySlots(String interviewerId) {
        Optional<User> interviewer = userRepository.findById(interviewerId);
        if (interviewer.isEmpty() || interviewer.get().getAvailability() == null) {
            return List.of();
        }
        Set<String> bookedStarts = sessionRepository.findByInterviewerIdAndStatusIn(interviewerId, ACTIVE_SESSION_STATUSES).stream()
                .map(Session::getStartTime)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        List<GeneratedSlot> slots = new ArrayList<>();
        for (String rawSlot : interviewer.get().getAvailability()) {
            if (rawSlot == null || rawSlot.isBlank() || bookedStarts.contains(rawSlot)) {
                continue;
            }
            Optional<OffsetDateTime> parsed = schedulingTimeService.tryParseStartTime(rawSlot);
            if (parsed.isPresent()) {
                if (!parsed.get().toInstant().isAfter(schedulingTimeService.nowInstant())) {
                    continue;
                }
                slots.add(new GeneratedSlot(
                        null,
                        parsed.get().toString(),
                        parsed.get().plusMinutes(LEGACY_DURATION_MINUTES).toString(),
                        LEGACY_DURATION_MINUTES
                ));
                continue;
            }
            slots.add(new GeneratedSlot(null, rawSlot.trim(), rawSlot.trim(), LEGACY_DURATION_MINUTES));
        }
        return slots;
    }

    private int effectiveSessionDurationMinutes(Session session) {
        return session.getDurationMinutes() == null || session.getDurationMinutes() <= 0
                ? LEGACY_DURATION_MINUTES
                : session.getDurationMinutes();
    }

    private Instant slotSortValue(GeneratedSlot slot) {
        return schedulingTimeService.tryParseStartTime(slot.startTime())
                .map(OffsetDateTime::toInstant)
                .orElse(Instant.MAX);
    }
}
