package com.interview.platform.service;

import com.interview.platform.dto.MeetingDtos;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class MeetingProviderService {

    private final List<MeetingProviderGateway> providers;
    private final String defaultProvider;

    public MeetingProviderService(
            List<MeetingProviderGateway> providers,
            @Value("${app.meetings.default-provider:JITSI}") String defaultProvider
    ) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(MeetingProviderGateway::getProviderKey))
                .toList();
        this.defaultProvider = normalize(defaultProvider, "JITSI");
    }

    public void provision(Session session, User interviewer, User interviewee) {
        MeetingProviderGateway provider = resolve(session.getMeetingProvider());
        provider.provision(session, interviewer, interviewee);
        if (session.getMeetingStatus() == null || session.getMeetingStatus().isBlank()) {
            session.setMeetingStatus("SCHEDULED");
        }
        if ((session.getJoinUrl() == null || session.getJoinUrl().isBlank()) && session.getMeetingLink() != null) {
            session.setJoinUrl(session.getMeetingLink());
        }
        if ((session.getMeetingLink() == null || session.getMeetingLink().isBlank()) && session.getJoinUrl() != null) {
            session.setMeetingLink(session.getJoinUrl());
        }
    }

    public MeetingDtos.MeetingAccessResponse buildAccess(Session session, User viewer, User interviewer, User interviewee) {
        return resolve(session.getMeetingProvider()).buildAccess(session, viewer, interviewer, interviewee);
    }

    public List<MeetingDtos.MeetingProviderOption> providerOptions() {
        String normalizedDefault = resolveDefaultProvider();
        return providers.stream()
                .map(provider -> new MeetingDtos.MeetingProviderOption(
                        provider.getProviderKey(),
                        provider.getLabel(),
                        provider.supportsEmbeddedExperience(),
                        provider.isEnabled(),
                        provider.getProviderKey().equals(normalizedDefault)
                ))
                .toList();
    }

    public String resolveDefaultProvider() {
        try {
            return resolve(defaultProvider).getProviderKey();
        } catch (IllegalArgumentException ignored) {
            return providers.stream().filter(MeetingProviderGateway::isEnabled)
                    .findFirst()
                    .map(MeetingProviderGateway::getProviderKey)
                    .orElse("JITSI");
        }
    }

    private MeetingProviderGateway resolve(String requestedProvider) {
        String key = normalize(requestedProvider, defaultProvider);
        return providers.stream()
                .filter(provider -> provider.getProviderKey().equals(key) && provider.isEnabled())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(key + " meetings are not available"));
    }

    private String normalize(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.toUpperCase(Locale.ROOT);
    }
}
