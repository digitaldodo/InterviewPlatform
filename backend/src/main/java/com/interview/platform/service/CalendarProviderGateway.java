package com.interview.platform.service;

import com.interview.platform.model.Session;
import com.interview.platform.model.User;

public interface CalendarProviderGateway {
    String getProviderKey();
    String getLabel();
    boolean isConfigured();
    String authorizationUrl(String state);
    CalendarTokenResponse exchangeCode(String code);
    CalendarTokenResponse refresh(String refreshToken);
    String upsertEvent(String accessToken, String eventId, Session session, User interviewer, User interviewee, User recipient, String meetingLink);
    void deleteEvent(String accessToken, String eventId);
    void revoke(String token);
    String scope();

    record CalendarTokenResponse(String accessToken, String refreshToken, int expiresInSeconds, String scope) {}
}
