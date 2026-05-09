package com.interview.platform.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.interview.platform.dto.AdminDtos;
import com.interview.platform.filter.RateLimitingFilter;
import com.interview.platform.model.CalendarConnection;
import com.interview.platform.model.CalendarEventSync;
import com.interview.platform.model.Session;
import com.interview.platform.repository.CalendarConnectionRepository;
import com.interview.platform.repository.CalendarEventSyncRepository;
import com.interview.platform.repository.SessionRepository;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OperationalDiagnosticsService {
    private final CacheManager cacheManager;
    private final NotificationService notificationService;
    private final SessionReminderService sessionReminderService;
    private final RateLimitingFilter rateLimitingFilter;
    private final CalendarConnectionRepository calendarConnectionRepository;
    private final CalendarEventSyncRepository calendarEventSyncRepository;
    private final SessionRepository sessionRepository;

    public OperationalDiagnosticsService(CacheManager cacheManager,
                                         NotificationService notificationService,
                                         SessionReminderService sessionReminderService,
                                         RateLimitingFilter rateLimitingFilter,
                                         CalendarConnectionRepository calendarConnectionRepository,
                                         CalendarEventSyncRepository calendarEventSyncRepository,
                                         SessionRepository sessionRepository) {
        this.cacheManager = cacheManager;
        this.notificationService = notificationService;
        this.sessionReminderService = sessionReminderService;
        this.rateLimitingFilter = rateLimitingFilter;
        this.calendarConnectionRepository = calendarConnectionRepository;
        this.calendarEventSyncRepository = calendarEventSyncRepository;
        this.sessionRepository = sessionRepository;
    }

    public AdminDtos.SystemDiagnosticsResponse snapshot() {
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        long totalBytes = runtime.totalMemory();
        long maxBytes = runtime.maxMemory();

        return new AdminDtos.SystemDiagnosticsResponse(
                Instant.now().toString(),
                new AdminDtos.JvmDiagnostics(
                        usedBytes,
                        totalBytes,
                        maxBytes,
                        runtime.availableProcessors(),
                        ManagementFactory.getRuntimeMXBean().getUptime()
                ),
                cacheStats(),
                rateLimitingFilter.diagnostics(),
                new AdminDtos.NotificationDiagnostics(
                        notificationService.activeEmitterCount(),
                        notificationService.activeUsersWithEmitters()
                ),
                sessionReminderService.diagnostics(),
                calendarOps()
        );
    }

    private AdminDtos.CalendarOpsDiagnostics calendarOps() {
        List<CalendarConnection> connections = safeList(calendarConnectionRepository::findAll);
        List<CalendarEventSync> syncs = safeList(calendarEventSyncRepository::findAll);
        List<Session> sessions = safeList(sessionRepository::findAll);
        long connected = connections.stream().filter(item -> "CONNECTED".equalsIgnoreCase(item.getStatus())).count();
        long disconnectedOrError = connections.stream().filter(item -> !"CONNECTED".equalsIgnoreCase(item.getStatus())).count();
        long syncFailures = syncs.stream().filter(item -> "ERROR".equalsIgnoreCase(item.getStatus()) || item.getLastError() != null).count();
        long reminderFailures = sessions.stream().filter(item -> item.getLastReminderFailureAt() != null).count();
        long anomalies = sessions.stream()
                .filter(item -> item.getStartTime() == null || item.getStartTime().isBlank()
                        || item.getInterviewerId() == null || item.getCandidateId() == null
                        || ("CONFIRMED".equalsIgnoreCase(item.getStatus()) && (item.getJoinUrl() == null || item.getJoinUrl().isBlank())))
                .count();
        return new AdminDtos.CalendarOpsDiagnostics(connected, disconnectedOrError, syncFailures, reminderFailures, anomalies);
    }

    private <T> List<T> safeList(java.util.function.Supplier<List<T>> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<AdminDtos.CacheDiagnostics> cacheStats() {
        List<AdminDtos.CacheDiagnostics> entries = new ArrayList<>();
        for (String cacheName : cacheManager.getCacheNames()) {
            org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
            if (!(springCache instanceof CaffeineCache caffeineCache)) {
                entries.add(new AdminDtos.CacheDiagnostics(cacheName, null, null, null, null));
                continue;
            }
            Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
            com.github.benmanes.caffeine.cache.stats.CacheStats stats = nativeCache.stats();
            entries.add(new AdminDtos.CacheDiagnostics(
                    cacheName,
                    nativeCache.estimatedSize(),
                    stats.hitCount(),
                    stats.missCount(),
                    stats.evictionCount()
            ));
        }
        return entries;
    }
}
