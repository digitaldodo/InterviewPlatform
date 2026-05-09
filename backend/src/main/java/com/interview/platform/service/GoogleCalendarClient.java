package com.interview.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class GoogleCalendarClient {
    static final String PROVIDER = "GOOGLE";
    static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a z", Locale.ENGLISH);

    private final ObjectMapper objectMapper;
    private final SchedulingTimeService schedulingTimeService;
    private final HttpClient httpClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendUrl;

    public GoogleCalendarClient(ObjectMapper objectMapper,
                                SchedulingTimeService schedulingTimeService,
                                @Value("${app.calendar.google.client-id:}") String clientId,
                                @Value("${app.calendar.google.client-secret:}") String clientSecret,
                                @Value("${app.calendar.google.redirect-uri:}") String redirectUri,
                                @Value("${app.frontend-url:http://localhost:5500}") String frontendUrl) {
        this.objectMapper = objectMapper;
        this.schedulingTimeService = schedulingTimeService;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri == null ? "" : redirectUri.trim();
        this.frontendUrl = frontendUrl == null || frontendUrl.isBlank() ? "http://localhost:5500" : frontendUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean configured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    public String authorizationUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    public TokenResponse exchangeCode(String code) {
        requireConfigured();
        return tokenRequest(Map.of(
                "code", code,
                "client_id", clientId,
                "client_secret", clientSecret,
                "redirect_uri", redirectUri,
                "grant_type", "authorization_code"
        ));
    }

    public TokenResponse refresh(String refreshToken) {
        requireConfigured();
        return tokenRequest(Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "refresh_token", refreshToken,
                "grant_type", "refresh_token"
        ));
    }

    public String upsertEvent(String accessToken, String eventId, Session session, User interviewer, User interviewee, User recipient, String meetingLink) {
        Map<String, Object> body = eventBody(session, interviewer, interviewee, recipient, meetingLink, false);
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json; charset=UTF-8");
            if (eventId == null || eventId.isBlank()) {
                builder.uri(URI.create("https://www.googleapis.com/calendar/v3/calendars/primary/events?sendUpdates=none"))
                        .POST(HttpRequest.BodyPublishers.ofString(json));
            } else {
                builder.uri(URI.create("https://www.googleapis.com/calendar/v3/calendars/primary/events/" + url(eventId) + "?sendUpdates=none"))
                        .PUT(HttpRequest.BodyPublishers.ofString(json));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 && eventId != null && !eventId.isBlank()) {
                return upsertEvent(accessToken, null, session, interviewer, interviewee, recipient, meetingLink);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GoogleCalendarException("Google Calendar event sync failed with status " + response.statusCode());
            }
            Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {});
            Object id = payload.get("id");
            if (id == null || String.valueOf(id).isBlank()) {
                throw new GoogleCalendarException("Google Calendar did not return an event id");
            }
            return String.valueOf(id);
        } catch (GoogleCalendarException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GoogleCalendarException("Google Calendar event sync failed", ex);
        }
    }

    public void deleteEvent(String accessToken, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/calendar/v3/calendars/primary/events/" + url(eventId) + "?sendUpdates=none"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .DELETE()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 410) {
                return;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GoogleCalendarException("Google Calendar delete failed with status " + response.statusCode());
            }
        } catch (GoogleCalendarException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GoogleCalendarException("Google Calendar delete failed", ex);
        }
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/revoke?token=" + url(token)))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // Disconnect must remain local-safe even if Google's revoke endpoint is unavailable.
        }
    }

    private TokenResponse tokenRequest(Map<String, String> form) {
        try {
            String body = form.entrySet().stream()
                    .map(entry -> url(entry.getKey()) + "=" + url(entry.getValue()))
                    .reduce((a, b) -> a + "&" + b)
                    .orElse("");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/token"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GoogleCalendarException("Google OAuth token request failed with status " + response.statusCode());
            }
            Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {});
            String accessToken = string(payload.get("access_token"));
            String refreshToken = string(payload.get("refresh_token"));
            Integer expiresIn = intValue(payload.get("expires_in"));
            String scope = string(payload.get("scope"));
            if (accessToken == null || accessToken.isBlank()) {
                throw new GoogleCalendarException("Google OAuth token response did not include an access token");
            }
            return new TokenResponse(accessToken, refreshToken, expiresIn == null ? 3600 : expiresIn, scope);
        } catch (GoogleCalendarException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GoogleCalendarException("Google OAuth token request failed", ex);
        }
    }

    private Map<String, Object> eventBody(Session session, User interviewer, User interviewee, User recipient, String meetingLink, boolean cancelled) {
        Instant start = schedulingTimeService.parseStartTime(session.getStartTime()).toInstant();
        Instant end = start.plus(Duration.ofMinutes(effectiveDurationMinutes(session)));
        String timeZone = recipient.getTimeZone() == null || recipient.getTimeZone().isBlank() ? "UTC" : recipient.getTimeZone();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", "InterviewPrep: " + safeTitle(session));
        body.put("description", eventDescription(session, interviewer, interviewee, recipient, meetingLink, start, end));
        body.put("location", meetingLink == null ? "" : meetingLink);
        body.put("start", Map.of("dateTime", OffsetDateTime.ofInstant(start, java.time.ZoneOffset.UTC).toString(), "timeZone", timeZone));
        body.put("end", Map.of("dateTime", OffsetDateTime.ofInstant(end, java.time.ZoneOffset.UTC).toString(), "timeZone", timeZone));
        body.put("transparency", "opaque");
        body.put("status", cancelled ? "cancelled" : "confirmed");
        body.put("source", Map.of("title", "InterviewPrep", "url", dashboardUrl(session)));
        body.put("extendedProperties", Map.of("private", Map.of(
                "interviewprepSessionId", session.getId() == null ? "" : session.getId(),
                "interviewprepProvider", "GOOGLE",
                "interviewprepMeetingProvider", session.getMeetingProvider() == null ? "JITSI" : session.getMeetingProvider(),
                "interviewprepStatus", session.getStatus() == null ? "" : session.getStatus(),
                "interviewprepUpdatedAt", session.getUpdatedAt() == null ? "" : session.getUpdatedAt().toString(),
                "interviewprepCancelledAt", session.getCancelledAt() == null ? "" : session.getCancelledAt().toString(),
                "interviewprepRescheduledAt", session.getRescheduledAt() == null ? "" : session.getRescheduledAt().toString()
        )));
        return body;
    }

    private String eventDescription(Session session, User interviewer, User interviewee, User recipient,
                                    String meetingLink, Instant start, Instant end) {
        ZoneId zone = recipientZone(recipient);
        ZonedDateTime localStart = start.atZone(zone);
        ZonedDateTime localEnd = end.atZone(zone);
        return String.join("\n",
                "InterviewPrep mock interview",
                "",
                "This calendar event was created by InterviewPrep. Booking, reschedule, and cancellation changes are synced from the platform when connected.",
                "Session: " + safeTitle(session),
                "Interviewer: " + displayName(interviewer) + " (role: interviewer)",
                "Interviewee: " + displayName(interviewee) + " (role: interviewee)",
                "Topics: " + String.join(", ", session.getTopics()),
                "Local time: " + DISPLAY_FORMATTER.format(localStart) + " - " + DISPLAY_FORMATTER.format(localEnd),
                "Timezone: " + zone.getId(),
                session.getNotes() == null || session.getNotes().isBlank() ? "" : "Notes: " + session.getNotes(),
                meetingLink == null || meetingLink.isBlank() ? "Meeting link: Open from InterviewPrep." : "Meeting link: " + meetingLink,
                "Meeting provider: " + (session.getMeetingProvider() == null || session.getMeetingProvider().isBlank() ? "JITSI" : session.getMeetingProvider()),
                session.getCancelledAt() == null ? "" : "Cancelled at: " + session.getCancelledAt(),
                session.getRescheduledAt() == null ? "" : "Rescheduled at: " + session.getRescheduledAt(),
                "Dashboard: " + dashboardUrl(session)
        ).replaceAll("\n{3,}", "\n\n");
    }

    private ZoneId recipientZone(User recipient) {
        String timeZone = recipient == null ? null : recipient.getTimeZone();
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (RuntimeException ignored) {
            return ZoneId.of("UTC");
        }
    }

    private String dashboardUrl(Session session) {
        return frontendUrl + "/pages/dashboard.html#/sessions" + (session.getId() == null ? "" : "?sessionId=" + session.getId());
    }

    private String safeTitle(Session session) {
        if (session.getTitle() != null && !session.getTitle().isBlank()) {
            return session.getTitle();
        }
        List<String> topics = session.getTopics();
        return topics == null || topics.isEmpty() ? "Mock interview" : String.join(", ", topics);
    }

    private String displayName(User user) {
        if (user == null) return "InterviewPrep user";
        String name = user.getName();
        if (name == null || name.isBlank()) name = user.getEmail();
        return name == null || name.isBlank() ? "InterviewPrep user" : name;
    }

    private int effectiveDurationMinutes(Session session) {
        Integer duration = session.getDurationMinutes();
        return duration == null || duration <= 0 ? 45 : duration;
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new GoogleCalendarException("Google Calendar integration is not configured");
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; }
    }

    private String url(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record TokenResponse(String accessToken, String refreshToken, int expiresInSeconds, String scope) {}

    public static class GoogleCalendarException extends RuntimeException {
        public GoogleCalendarException(String message) { super(message); }
        public GoogleCalendarException(String message, Throwable cause) { super(message, cause); }
    }
}
