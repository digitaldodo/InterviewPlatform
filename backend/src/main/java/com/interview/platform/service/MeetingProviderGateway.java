package com.interview.platform.service;

import com.interview.platform.dto.MeetingDtos;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;

public interface MeetingProviderGateway {

    String getProviderKey();

    String getLabel();

    boolean isEnabled();

    boolean supportsEmbeddedExperience();

    void provision(Session session, User interviewer, User interviewee);

    MeetingDtos.MeetingAccessResponse buildAccess(Session session, User viewer, User interviewer, User interviewee);
}
