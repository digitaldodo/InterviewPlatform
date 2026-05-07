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

    public record GeneratedSlot(String availabilityId, String startTime, String endTime, int durationMinutes, String status) {}
    public record BookingResolution(String startTime, int durationMinutes) {}

    private static final List<String> ACTIVE_SESSION_STATUSES = List.of("PENDING", "CONFIRMED");
    private static final int LEGACY_DURATION_MINUTES = 45;
    private static final String SLOT_AVAILABLE = "AVAILABLE";
    private static final String SLOT_BOOKED = "BOOKED";

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
        return generatedSlots(interviewerId, days, false).stream()
                .map(GeneratedSlot::startTime)
                .toList();
    }

    public List<AvailabilityDtos.GeneratedSlotResponse> generatedSlotResponses(String interviewerId, Integer days) {
        return generatedSlotResponses(interviewerId, days, false);
    }

    public List<AvailabilityDtos.GeneratedSlotResponse> generatedSlotResponses(String interviewerId, Integer days, boolean includeUnavailable) {
        return generatedSlots(interviewerId, days, includeUnavailable).stream()
                .map(slot -> new AvailabilityDtos.GeneratedSlotResponse(
                        slot.availabilityId(),
                        slot.startTime(),
                        slot.endTime(),
                        slot.durationMinutes(),
                        slot.status()))
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

    private List<GeneratedSlot> generatedSlots(String interviewerId, Integer days, boolean includeUnavailable) {
        List<InterviewerAvailability> schedules = availabilityRepository.findByInterviewerIdOrderByDayOfWeekAscStartTimeAsc(interviewerId);
        if (schedules.isEmpty()) {
            return legacySlots(interviewerId, includeUnavailable);
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
        return applySlotStatuses(interviewerId, new ArrayList<>(slots.values()), includeUnavailable).stream()
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
        return applySlotStatuses(interviewerId, new ArrayList<>(slots.values()), false).stream()
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
                        availability.getDurationMinutes(),
                        SLOT_AVAILABLE
                );
                slots.putIfAbsent(slot.startTime(), slot);
            }
            slotStart = slotStart.plusMinutes(availability.getDurationMinutes());
        }
    }

    private List<GeneratedSlot> applySlotStatuses(String interviewerId, List<GeneratedSlot> slots, boolean includeUnavailable) {
        List<Session> activeSessions = sessionRepository.findByInterviewerIdAndStatusIn(interviewerId, ACTIVE_SESSION_STATUSES);
        return slots.stream()
                .map(slot -> activeSessions.stream().anyMatch(session -> conflicts(session, slot))
                        ? withStatus(slot, SLOT_BOOKED)
                        : slot)
                .filter(slot -> includeUnavailable || SLOT_AVAILABLE.equals(slot.status()))
                .toList();
    }

    private GeneratedSlot withStatus(GeneratedSlot slot, String status) {
        return new GeneratedSlot(slot.availabilityId(), slot.startTime(), slot.endTime(), slot.durationMinutes(), status);
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

    private List<GeneratedSlot> legacySlots(String interviewerId, boolean includeUnavailable) {
        Optional<User> interviewer = userRepository.findById(interviewerId);
        if (interviewer.isEmpty() || interviewer.get().getAvailability() == null) {
            return List.of();
        }
        List<Session> activeSessions = sessionRepository.findByInterviewerIdAndStatusIn(interviewerId, ACTIVE_SESSION_STATUSES);
        List<GeneratedSlot> slots = new ArrayList<>();
        for (String rawSlot : interviewer.get().getAvailability()) {
            if (rawSlot == null || rawSlot.isBlank()) {
                continue;
            }
            Optional<OffsetDateTime> parsed = schedulingTimeService.tryParseStartTime(rawSlot);
            if (parsed.isPresent()) {
                if (!parsed.get().toInstant().isAfter(schedulingTimeService.nowInstant())) {
                    continue;
                }
                GeneratedSlot slot = new GeneratedSlot(
                        null,
                        parsed.get().toString(),
                        parsed.get().plusMinutes(LEGACY_DURATION_MINUTES).toString(),
                        LEGACY_DURATION_MINUTES,
                        SLOT_AVAILABLE
                );
                boolean booked = activeSessions.stream().anyMatch(session -> conflicts(session, slot));
                if (!booked || includeUnavailable) {
                    slots.add(booked ? withStatus(slot, SLOT_BOOKED) : slot);
                }
                continue;
            }
            GeneratedSlot slot = new GeneratedSlot(null, rawSlot.trim(), rawSlot.trim(), LEGACY_DURATION_MINUTES, SLOT_AVAILABLE);
            boolean booked = activeSessions.stream().anyMatch(session -> rawSlot.trim().equals(session.getStartTime()));
            if (!booked || includeUnavailable) {
                slots.add(booked ? withStatus(slot, SLOT_BOOKED) : slot);
            }
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
