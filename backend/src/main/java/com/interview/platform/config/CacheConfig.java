package com.interview.platform.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {
    public static final String USER_PROFILE_CACHE = "userProfile";
    public static final String INTERVIEWER_CARD_CACHE = "interviewerCard";
    public static final String INTERVIEWER_TOP_RATED_CACHE = "interviewerTopRated";
    public static final String INTERVIEWER_RECOMMENDED_CACHE = "interviewerRecommended";
    public static final String INTERVIEWER_FILTER_OPTIONS_CACHE = "interviewerFilterOptions";
    public static final String INTERVIEWER_PUBLIC_PROFILE_CACHE = "interviewerPublicProfile";
    public static final String INTERVIEWER_PUBLIC_SUMMARY_CACHE = "interviewerPublicSummary";
    public static final String INTERVIEWER_AUTOCOMPLETE_CACHE = "interviewerAutocomplete";
    public static final String AVAILABILITY_SLOTS_CACHE = "availabilitySlots";
    public static final String AVAILABILITY_SLOT_RESPONSES_CACHE = "availabilitySlotResponses";
    public static final String ANALYTICS_SUMMARY_CACHE = "analyticsSummary";

    @Bean
    public CacheManager cacheManager(
            @Value("${app.cache.ttl-seconds:120}") long ttlSeconds,
            @Value("${app.cache.max-entries:5000}") long maxEntries
    ) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(Duration.ofSeconds(Math.max(15, ttlSeconds)))
                .maximumSize(Math.max(100, maxEntries)));
        manager.setCacheNames(List.of(
                USER_PROFILE_CACHE,
                INTERVIEWER_CARD_CACHE,
                INTERVIEWER_TOP_RATED_CACHE,
                INTERVIEWER_RECOMMENDED_CACHE,
                INTERVIEWER_FILTER_OPTIONS_CACHE,
                INTERVIEWER_PUBLIC_PROFILE_CACHE,
                INTERVIEWER_PUBLIC_SUMMARY_CACHE,
                INTERVIEWER_AUTOCOMPLETE_CACHE,
                AVAILABILITY_SLOTS_CACHE,
                AVAILABILITY_SLOT_RESPONSES_CACHE,
                ANALYTICS_SUMMARY_CACHE
        ));
        return manager;
    }
}
