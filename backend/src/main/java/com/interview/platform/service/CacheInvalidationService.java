package com.interview.platform.service;

import com.interview.platform.config.CacheConfig;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class CacheInvalidationService {
    private final CacheManager cacheManager;

    public CacheInvalidationService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictUserProfile(String userId) {
        evict(CacheConfig.USER_PROFILE_CACHE, userId);
    }

    public void evictInterviewerCaches(String interviewerId, String username) {
        evict(CacheConfig.INTERVIEWER_CARD_CACHE, interviewerId);
        evict(CacheConfig.INTERVIEWER_PUBLIC_PROFILE_CACHE, normalize(username));
        clearAll(
                CacheConfig.INTERVIEWER_TOP_RATED_CACHE,
                CacheConfig.INTERVIEWER_RECOMMENDED_CACHE,
                CacheConfig.INTERVIEWER_FILTER_OPTIONS_CACHE,
                CacheConfig.INTERVIEWER_PUBLIC_SUMMARY_CACHE,
                CacheConfig.INTERVIEWER_AUTOCOMPLETE_CACHE
        );
    }

    public void evictAvailabilityCaches(String interviewerId) {
        Cache slotsCache = cacheManager.getCache(CacheConfig.AVAILABILITY_SLOTS_CACHE);
        Cache responsesCache = cacheManager.getCache(CacheConfig.AVAILABILITY_SLOT_RESPONSES_CACHE);
        if (slotsCache != null) {
            slotsCache.clear();
        }
        if (responsesCache != null) {
            responsesCache.clear();
        }
        evictInterviewerCaches(interviewerId, null);
    }

    public void evictAnalytics(String userId) {
        Cache cache = cacheManager.getCache(CacheConfig.ANALYTICS_SUMMARY_CACHE);
        if (cache == null || userId == null || userId.isBlank()) {
            return;
        }
        cache.evictIfPresent(userId + ":INTERVIEWEE");
        cache.evictIfPresent(userId + ":INTERVIEWER");
        cache.evictIfPresent(userId + ":");
    }

    public void evictAnalyticsForParticipants(String... userIds) {
        for (String userId : userIds) {
            evictAnalytics(userId);
            evictUserProfile(userId);
        }
    }

    private void clearAll(String... cacheNames) {
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private void evict(String cacheName, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evictIfPresent(key);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }
}
