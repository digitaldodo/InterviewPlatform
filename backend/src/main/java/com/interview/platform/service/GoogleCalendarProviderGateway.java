package com.interview.platform.service;

import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarProviderGateway implements CalendarProviderGateway {
    private final GoogleCalendarClient googleCalendarClient;

    public GoogleCalendarProviderGateway(GoogleCalendarClient googleCalendarClient) {
        this.googleCalendarClient = googleCalendarClient;
    }

    @Override
    public String getProviderKey() {
        return GoogleCalendarClient.PROVIDER;
    }

    @Override
    public String getLabel() {
        return "Google Calendar";
    }

    @Override
    public boolean isConfigured() {
        return googleCalendarClient.configured();
    }

    @Override
    public String authorizationUrl(String state) {
        return googleCalendarClient.authorizationUrl(state);
    }

    @Override
    public CalendarTokenResponse exchangeCode(String code) {
        GoogleCalendarClient.TokenResponse token = googleCalendarClient.exchangeCode(code);
        return new CalendarTokenResponse(token.accessToken(), token.refreshToken(), token.expiresInSeconds(), token.scope());
    }

    @Override
    public CalendarTokenResponse refresh(String refreshToken) {
        GoogleCalendarClient.TokenResponse token = googleCalendarClient.refresh(refreshToken);
        return new CalendarTokenResponse(token.accessToken(), token.refreshToken(), token.expiresInSeconds(), token.scope());
    }

    @Override
    public String upsertEvent(String accessToken, String eventId, Session session, User interviewer, User interviewee, User recipient, String meetingLink) {
        return googleCalendarClient.upsertEvent(accessToken, eventId, session, interviewer, interviewee, recipient, meetingLink);
    }

    @Override
    public void deleteEvent(String accessToken, String eventId) {
        googleCalendarClient.deleteEvent(accessToken, eventId);
    }

    @Override
    public void revoke(String token) {
        googleCalendarClient.revoke(token);
    }

    @Override
    public String scope() {
        return GoogleCalendarClient.SCOPE;
    }
}
