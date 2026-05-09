package com.interview.platform.service;

import com.interview.platform.dto.CalendarDtos;
import com.interview.platform.model.CalendarConnection;
import com.interview.platform.model.CalendarEventSync;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.CalendarConnectionRepository;
import com.interview.platform.repository.CalendarEventSyncRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class CalendarIntegrationService {
    private static final Logger log = LoggerFactory.getLogger(CalendarIntegrationService.class);
    private static final String GOOGLE = GoogleCalendarClient.PROVIDER;

    private final CalendarConnectionRepository connectionRepository;
    private final CalendarEventSyncRepository eventSyncRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final CalendarTokenCryptoService tokenCryptoService;
    private final List<CalendarProviderGateway> providers;
    private final SecretKey stateKey;
    private final String frontendUrl;

    public CalendarIntegrationService(CalendarConnectionRepository connectionRepository,
                                      CalendarEventSyncRepository eventSyncRepository,
                                      SessionRepository sessionRepository,
                                      UserRepository userRepository,
                                      CalendarTokenCryptoService tokenCryptoService,
                                      List<CalendarProviderGateway> providers,
                                      @Value("${app.calendar.oauth-state-secret:${app.jwt.secret}}") String stateSecret,
                                      @Value("${app.frontend-url:http://localhost:5500}") String frontendUrl) {
        this.connectionRepository = connectionRepository;
        this.eventSyncRepository = eventSyncRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.tokenCryptoService = tokenCryptoService;
        this.providers = providers.stream()
                .sorted(Comparator.comparing(CalendarProviderGateway::getProviderKey))
                .toList();
        String normalized = stateSecret == null || stateSecret.length() < 32
                ? "change-this-development-oauth-state-secret"
                : stateSecret;
        this.stateKey = new SecretKeySpec(normalized.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.frontendUrl = frontendUrl == null || frontendUrl.isBlank() ? "http://localhost:5500" : frontendUrl.replaceAll("/+$", "");
    }

    public CalendarDtos.CalendarConnectionStatus googleStatus(User user) {
        CalendarProviderGateway provider = provider(GOOGLE);
        Optional<CalendarConnection> connection = connectionRepository.findByProviderAndUserId(GOOGLE, user.getId());
        return connection
                .map(item -> new CalendarDtos.CalendarConnectionStatus(
                        GOOGLE,
                        provider.isConfigured(),
                        "CONNECTED".equals(item.getStatus()),
                        item.getStatus(),
                        item.getAccountEmail(),
                        item.getConnectedAt(),
                        item.getLastSyncAt(),
                        item.getLastSyncStatus(),
                        item.getLastSyncMessage()
                ))
                .orElseGet(() -> new CalendarDtos.CalendarConnectionStatus(
                        GOOGLE,
                        provider.isConfigured(),
                        false,
                        "DISCONNECTED",
                        null,
                        null,
                        null,
                        null,
                        provider.isConfigured() ? "Google Calendar is not connected." : "Google Calendar is not configured."
                ));
    }

    public CalendarDtos.CalendarSettingsResponse settings(User user) {
        List<CalendarDtos.CalendarProviderOption> providerOptions = providers.stream()
                .map(provider -> providerStatus(provider, user))
                .toList();
        return new CalendarDtos.CalendarSettingsResponse(
                providerOptions,
                user.getTimeZone(),
                user.getPreferredMeetingProvider(),
                user.getEmailRemindersEnabled(),
                user.getInAppRemindersEnabled(),
                user.getCalendarAutoSyncEnabled(),
                user.getReminderOffsetsMinutes()
        );
    }

    public CalendarDtos.CalendarConnectResponse googleConnect(User user) {
        String state = signedState(user.getId());
        return new CalendarDtos.CalendarConnectResponse(provider(GOOGLE).authorizationUrl(state));
    }

    public String handleGoogleCallback(String code, String state, String error) {
        if (error != null && !error.isBlank()) {
            return redirectUrl("error", error);
        }
        try {
            String userId = parseStateUserId(state);
            User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
            CalendarProviderGateway provider = provider(GOOGLE);
            CalendarProviderGateway.CalendarTokenResponse token = provider.exchangeCode(code);
            CalendarConnection connection = connectionRepository.findByProviderAndUserId(GOOGLE, userId).orElseGet(CalendarConnection::new);
            Instant now = Instant.now();
            connection.setUserId(userId);
            connection.setProvider(GOOGLE);
            connection.setAccountEmail(user.getEmail());
            connection.setEncryptedAccessToken(tokenCryptoService.encrypt(token.accessToken()));
            if (token.refreshToken() != null && !token.refreshToken().isBlank()) {
                connection.setEncryptedRefreshToken(tokenCryptoService.encrypt(token.refreshToken()));
            }
            connection.setAccessTokenExpiresAt(now.plusSeconds(Math.max(60, token.expiresInSeconds() - 60L)));
            connection.setScopes(token.scope() == null || token.scope().isBlank() ? provider.scope() : token.scope());
            connection.setStatus("CONNECTED");
            connection.setConnectedAt(connection.getConnectedAt() == null ? now : connection.getConnectedAt());
            connection.setUpdatedAt(now);
            connection.setLastSyncStatus("CONNECTED");
            connection.setLastSyncMessage("Google Calendar connected.");
            connectionRepository.save(connection);
            CalendarDtos.CalendarSyncResponse sync = syncUserSessions(user);
            return redirectUrl("connected", "Synced " + sync.synced() + " session(s).");
        } catch (RuntimeException ex) {
            log.warn("Google Calendar OAuth callback failed safely: {}", ex.getMessage());
            return redirectUrl("error", "Google Calendar connection failed.");
        }
    }

    public CalendarDtos.CalendarSyncResponse disconnectGoogle(User user) {
        connectionRepository.findByProviderAndUserId(GOOGLE, user.getId()).ifPresent(connection -> {
            CalendarProviderGateway provider = provider(GOOGLE);
            provider.revoke(tokenCryptoService.decrypt(connection.getEncryptedRefreshToken()));
            provider.revoke(tokenCryptoService.decrypt(connection.getEncryptedAccessToken()));
        });
        eventSyncRepository.deleteByProviderAndUserId(GOOGLE, user.getId());
        connectionRepository.deleteByProviderAndUserId(GOOGLE, user.getId());
        return new CalendarDtos.CalendarSyncResponse(true, 0, 0, "Google Calendar disconnected.", Instant.now());
    }

    public CalendarDtos.CalendarSyncResponse syncUserSessions(User user) {
        Optional<CalendarConnection> connection = connectionRepository.findByProviderAndUserId(GOOGLE, user.getId());
        if (connection.isEmpty() || !"CONNECTED".equals(connection.get().getStatus())) {
            return new CalendarDtos.CalendarSyncResponse(false, 0, 0, "Google Calendar is not connected.", Instant.now());
        }
        List<Session> sessions = sessionRepository.findByInterviewerIdOrCandidateId(user.getId(), user.getId());
        int synced = 0;
        int failed = 0;
        for (Session session : sessions) {
            try {
                syncSessionForUser(session, user.getId());
                synced += 1;
            } catch (RuntimeException ex) {
                failed += 1;
            }
        }
        CalendarConnection saved = connection.get();
        markConnectionSync(saved, failed == 0 ? "SYNCED" : "PARTIAL", failed == 0 ? "Google Calendar sync complete." : "Some sessions could not be synced.");
        return new CalendarDtos.CalendarSyncResponse(true, synced, failed, saved.getLastSyncMessage(), saved.getLastSyncAt());
    }

    public void syncSessionForParticipantsSafely(Session session) {
        if (session == null) return;
        syncSessionForUserSafely(session, session.getCandidateId());
        syncSessionForUserSafely(session, session.getInterviewerId());
    }

    private void syncSessionForUserSafely(Session session, String userId) {
        try {
            syncSessionForUser(session, userId);
        } catch (RuntimeException ex) {
            log.warn("Google Calendar sync failed safely for session {} user {}: {}", session.getId(), userId, ex.getMessage());
        }
    }

    private void syncSessionForUser(Session session, String userId) {
        if (userId == null || userId.isBlank() || session.getId() == null || session.getId().isBlank()) {
            return;
        }
        CalendarConnection connection = connectionRepository.findByProviderAndUserId(GOOGLE, userId).orElse(null);
        if (connection == null || !"CONNECTED".equals(connection.getStatus())) {
            return;
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Calendar user not found"));
        if (!user.getCalendarAutoSyncEnabled()) {
            return;
        }
        User interviewer = userRepository.findById(session.getInterviewerId()).orElseThrow(() -> new IllegalStateException("Interviewer not found"));
        User interviewee = userRepository.findById(session.getCandidateId()).orElseThrow(() -> new IllegalStateException("Interviewee not found"));
        String accessToken = accessToken(connection);
        CalendarProviderGateway provider = provider(GOOGLE);
        CalendarEventSync eventSync = eventSyncRepository.findByProviderAndUserIdAndSessionId(GOOGLE, userId, session.getId()).orElseGet(CalendarEventSync::new);
        eventSync.setProvider(GOOGLE);
        eventSync.setUserId(userId);
        eventSync.setSessionId(session.getId());
        try {
            if ("CANCELLED".equalsIgnoreCase(session.getStatus())) {
                provider.deleteEvent(accessToken, eventSync.getExternalEventId());
                eventSync.setStatus("CANCELLED");
            } else {
                String meetingLink = userId.equals(session.getInterviewerId()) ? interviewerMeetingLink(session) : session.getJoinUrl();
                String eventId = provider.upsertEvent(accessToken, eventSync.getExternalEventId(), session, interviewer, interviewee, user, meetingLink);
                eventSync.setExternalEventId(eventId);
                eventSync.setStatus("SYNCED");
            }
            eventSync.setLastSyncedAt(Instant.now());
            eventSync.setLastError(null);
            eventSyncRepository.save(eventSync);
            markConnectionSync(connection, "SYNCED", "Google Calendar sync complete.");
        } catch (RuntimeException ex) {
            eventSync.setStatus("ERROR");
            eventSync.setLastError(ex.getMessage());
            eventSync.setLastSyncedAt(Instant.now());
            eventSyncRepository.save(eventSync);
            markConnectionSync(connection, "ERROR", "Google Calendar sync failed. ICS invites are still available.");
            throw ex;
        }
    }

    private String accessToken(CalendarConnection connection) {
        Instant expiresAt = connection.getAccessTokenExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(Instant.now().plusSeconds(90))) {
            String refreshToken = tokenCryptoService.decrypt(connection.getEncryptedRefreshToken());
            if (refreshToken == null || refreshToken.isBlank()) {
                connection.setStatus("ERROR");
                connection.setLastSyncStatus("ERROR");
                connection.setLastSyncMessage("Google Calendar needs to be reconnected.");
                connection.setUpdatedAt(Instant.now());
                connectionRepository.save(connection);
                throw new IllegalStateException("Google Calendar refresh token is unavailable");
            }
            try {
                CalendarProviderGateway.CalendarTokenResponse refreshed = provider(connection.getProvider()).refresh(refreshToken);
                connection.setEncryptedAccessToken(tokenCryptoService.encrypt(refreshed.accessToken()));
                connection.setAccessTokenExpiresAt(Instant.now().plusSeconds(Math.max(60, refreshed.expiresInSeconds() - 60L)));
                connection.setStatus("CONNECTED");
                connection.setUpdatedAt(Instant.now());
                connectionRepository.save(connection);
            } catch (RuntimeException ex) {
                connection.setStatus("ERROR");
                connection.setLastSyncStatus("ERROR");
                connection.setLastSyncMessage("Google Calendar permission expired or was revoked. Reconnect to resume sync.");
                connection.setUpdatedAt(Instant.now());
                connectionRepository.save(connection);
                throw ex;
            }
        }
        return tokenCryptoService.decrypt(connection.getEncryptedAccessToken());
    }

    private void markConnectionSync(CalendarConnection connection, String status, String message) {
        connection.setLastSyncAt(Instant.now());
        connection.setLastSyncStatus(status);
        connection.setLastSyncMessage(message);
        connection.setUpdatedAt(Instant.now());
        connectionRepository.save(connection);
    }

    private String parseStateUserId(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("OAuth state is missing");
        }
        String[] parts = state.split("\\.", 2);
        if (parts.length != 2 || !constantTimeEquals(parts[1], sign(parts[0]))) {
            throw new IllegalArgumentException("OAuth state is invalid");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] values = payload.split(":", 3);
        if (values.length < 2) {
            throw new IllegalArgumentException("OAuth state payload is invalid");
        }
        long expiresAt = Long.parseLong(values[1]);
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new IllegalArgumentException("OAuth state expired");
        }
        return values[0];
    }

    private String signedState(String userId) {
        String payload = userId + ":" + Instant.now().plusSeconds(600).getEpochSecond() + ":" + UUID.randomUUID();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(stateKey);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("OAuth state signing failed", ex);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String redirectUrl(String status, String message) {
        return frontendUrl + "/pages/dashboard.html#/profile?calendar=" + url(status) + "&message=" + url(message);
    }

    private CalendarDtos.CalendarProviderOption providerStatus(CalendarProviderGateway provider, User user) {
        Optional<CalendarConnection> connection = connectionRepository.findByProviderAndUserId(provider.getProviderKey(), user.getId());
        return connection
                .map(item -> new CalendarDtos.CalendarProviderOption(
                        provider.getProviderKey(),
                        provider.getLabel(),
                        provider.isConfigured(),
                        "CONNECTED".equals(item.getStatus()),
                        item.getStatus(),
                        item.getAccountEmail(),
                        item.getConnectedAt(),
                        item.getLastSyncAt(),
                        item.getLastSyncStatus(),
                        item.getLastSyncMessage()
                ))
                .orElseGet(() -> new CalendarDtos.CalendarProviderOption(
                        provider.getProviderKey(),
                        provider.getLabel(),
                        provider.isConfigured(),
                        false,
                        "DISCONNECTED",
                        null,
                        null,
                        null,
                        null,
                        provider.isConfigured() ? provider.getLabel() + " is not connected." : provider.getLabel() + " is not configured."
                ));
    }

    private CalendarProviderGateway provider(String providerKey) {
        String key = providerKey == null || providerKey.isBlank() ? GOOGLE : providerKey.trim().toUpperCase(Locale.ROOT);
        return providers.stream()
                .filter(provider -> provider.getProviderKey().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(key + " calendar provider is not available"));
    }

    private String interviewerMeetingLink(Session session) {
        return session.getHostUrl() == null || session.getHostUrl().isBlank() ? session.getJoinUrl() : session.getHostUrl();
    }

    private String url(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
