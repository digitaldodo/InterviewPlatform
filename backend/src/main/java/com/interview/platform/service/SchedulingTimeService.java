package com.interview.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Service
public class SchedulingTimeService {

    private final ZoneId zoneId;
    private final Clock clock;

    public SchedulingTimeService(@Value("${app.scheduling.zone:UTC}") String zoneIdValue) {
        this(ZoneId.of(zoneIdValue), Clock.system(ZoneId.of(zoneIdValue)));
    }

    public SchedulingTimeService(ZoneId zoneId, Clock clock) {
        this.zoneId = zoneId;
        this.clock = clock;
    }

    public Optional<OffsetDateTime> tryParseStartTime(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        try {
            return Optional.of(OffsetDateTime.parse(trimmed));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Optional.of(Instant.parse(trimmed).atZone(zoneId).toOffsetDateTime());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Optional.of(LocalDateTime.parse(trimmed).atZone(zoneId).toOffsetDateTime());
        } catch (DateTimeParseException ignored) {
        }
        return Optional.empty();
    }

    public OffsetDateTime parseStartTime(String value) {
        return tryParseStartTime(value)
                .orElseThrow(() -> new IllegalArgumentException("Start time must be a valid ISO date-time"));
    }

    public boolean isPast(String value) {
        return tryParseStartTime(value)
                .map(time -> !time.toInstant().isAfter(Instant.now(clock)))
                .orElse(false);
    }

    public String format(ZonedDateTime value) {
        return value.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public Instant nowInstant() {
        return Instant.now(clock);
    }

    public Clock getClock() {
        return clock;
    }
}
