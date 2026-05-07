package com.interview.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.platform.dto.MeetingDtos;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@Component
public class ZoomMeetingProvider implements MeetingProviderGateway {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String accountId;
    private final String clientId;
    private final String clientSecret;
    private final String userId;

    public ZoomMeetingProvider(
            ObjectMapper objectMapper,
            @Value("${app.meetings.zoom.account-id:}") String accountId,
            @Value("${app.meetings.zoom.client-id:}") String clientId,
            @Value("${app.meetings.zoom.client-secret:}") String clientSecret,
            @Value("${app.meetings.zoom.user-id:}") String userId
    ) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = objectMapper;
        this.accountId = blankToEmpty(accountId);
        this.clientId = blankToEmpty(clientId);
        this.clientSecret = blankToEmpty(clientSecret);
        this.userId = blankToEmpty(userId);
    }

    @Override
    public String getProviderKey() {
        return "ZOOM";
    }

    @Override
    public String getLabel() {
        return "Zoom";
    }

    @Override
    public boolean isEnabled() {
        return !accountId.isBlank() && !clientId.isBlank() && !clientSecret.isBlank() && !userId.isBlank();
    }

    @Override
    public boolean supportsEmbeddedExperience() {
        return false;
    }

    @Override
    public void provision(Session session, User interviewer, User interviewee) {
        if (!isEnabled()) {
            throw new IllegalArgumentException("Zoom meetings are not configured for this environment");
        }
        try {
            String token = fetchAccessToken();
            JsonNode created = createMeeting(token, session, interviewer, interviewee);
            session.setMeetingProvider(getProviderKey());
            session.setMeetingId(created.path("id").asText());
            session.setJoinUrl(created.path("join_url").asText(""));
            session.setHostUrl(created.path("start_url").asText(""));
            session.setMeetingPasscode(created.path("password").asText(""));
            session.setMeetingStatus(normalizeStatus(session.getMeetingStatus()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Zoom meeting provisioning failed");
        } catch (IOException ex) {
            throw new IllegalArgumentException("Zoom meeting provisioning failed");
        }
    }

    @Override
    public MeetingDtos.MeetingAccessResponse buildAccess(Session session, User viewer, User interviewer, User interviewee) {
        boolean host = viewer.getId().equals(session.getInterviewerId());
        return new MeetingDtos.MeetingAccessResponse(
                session.getId(),
                session.getInterviewType() != null && !session.getInterviewType().isBlank() ? session.getInterviewType() : session.getTitle(),
                getProviderKey(),
                getLabel(),
                session.getMeetingId(),
                normalizeStatus(session.getMeetingStatus()),
                host ? "HOST" : "PARTICIPANT",
                label(interviewer),
                label(interviewee),
                session.getJoinUrl(),
                session.getHostUrl(),
                host && session.getHostUrl() != null && !session.getHostUrl().isBlank() ? session.getHostUrl() : session.getJoinUrl(),
                session.getMeetingPasscode(),
                session.getStartTime(),
                session.getDurationMinutes(),
                session.getMeetingStartedAt(),
                session.getMeetingEndedAt(),
                false,
                true,
                null,
                null,
                null,
                label(viewer),
                viewer.getEmail(),
                null
        );
    }

    private String fetchAccessToken() throws IOException, InterruptedException {
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String query = "grant_type=account_credentials&account_id=" + urlEncode(accountId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://zoom.us/oauth/token?" + query))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Zoom auth failed with status " + response.statusCode());
        }
        return objectMapper.readTree(response.body()).path("access_token").asText();
    }

    private JsonNode createMeeting(String token, Session session, User interviewer, User interviewee) throws IOException, InterruptedException {
        Map<String, Object> payload = Map.of(
                "topic", (session.getInterviewType() == null || session.getInterviewType().isBlank() ? "Interview session" : session.getInterviewType()) + " interview",
                "type", 2,
                "start_time", zoomStartTime(session.getStartTime()),
                "duration", session.getDurationMinutes() == null ? 45 : session.getDurationMinutes(),
                "timezone", "UTC",
                "agenda", session.getNotes() == null ? "" : session.getNotes(),
                "settings", Map.of(
                        "join_before_host", false,
                        "waiting_room", true,
                        "participant_video", false,
                        "host_video", true,
                        "mute_upon_entry", true
                )
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.zoom.us/v2/users/" + urlEncode(userId) + "/meetings"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Zoom meeting create failed with status " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String zoomStartTime(String startTime) {
        if (startTime == null || startTime.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC).plusHours(1).withNano(0).toString();
        }
        try {
            return OffsetDateTime.parse(startTime).withOffsetSameInstant(ZoneOffset.UTC).withNano(0).toString();
        } catch (DateTimeParseException ignored) {
            return startTime;
        }
    }

    private String label(User user) {
        if (user == null) {
            return "Participant";
        }
        String value = user.getName();
        if (value == null || value.isBlank()) {
            value = user.getUsername();
        }
        return value == null || value.isBlank() ? "Participant" : value;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "SCHEDULED";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
