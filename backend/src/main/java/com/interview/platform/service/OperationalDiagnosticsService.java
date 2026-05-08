package com.interview.platform.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.interview.platform.dto.AdminDtos;
import com.interview.platform.filter.RateLimitingFilter;
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

    public OperationalDiagnosticsService(CacheManager cacheManager,
                                         NotificationService notificationService,
                                         SessionReminderService sessionReminderService,
                                         RateLimitingFilter rateLimitingFilter) {
        this.cacheManager = cacheManager;
        this.notificationService = notificationService;
        this.sessionReminderService = sessionReminderService;
        this.rateLimitingFilter = rateLimitingFilter;
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
                sessionReminderService.diagnostics()
        );
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
