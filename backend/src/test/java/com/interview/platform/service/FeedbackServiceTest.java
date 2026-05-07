package com.interview.platform.service;

import com.interview.platform.model.Feedback;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private InterviewReportService interviewReportService;

    @Test
    void rejectsDuplicateFeedbackFromSameReviewerForSession() {
        FeedbackService service = new FeedbackService(feedbackRepository, sessionRepository, userRepository, notificationService, interviewReportService);
        User actor = new User();
        actor.setId("candidate-1");
        Session session = new Session();
        session.setId("session-1");
        session.setCandidateId("candidate-1");
        session.setInterviewerId("interviewer-1");
        session.setStatus("COMPLETED");

        Feedback feedback = new Feedback();
        feedback.setSessionId("session-1");
        feedback.setComments("Helpful session");
        feedback.setRating(5);

        when(sessionRepository.existsById("session-1")).thenReturn(true);
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));
        when(feedbackRepository.existsBySessionIdAndReviewerId("session-1", "candidate-1")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.submitFeedback(actor, feedback));
        assertEquals("You have already submitted feedback for this session", ex.getMessage());
    }

    @Test
    void marksCompletedCandidateFeedbackAsPublicReview() {
        FeedbackService service = new FeedbackService(feedbackRepository, sessionRepository, userRepository, notificationService, interviewReportService);
        User actor = new User();
        actor.setId("candidate-1");
        Session session = new Session();
        session.setId("session-1");
        session.setCandidateId("candidate-1");
        session.setInterviewerId("interviewer-1");
        session.setStatus("COMPLETED");
        session.setTitle("System Design");

        User interviewer = new User();
        interviewer.setId("interviewer-1");

        Feedback feedback = new Feedback();
        feedback.setSessionId("session-1");
        feedback.setComments("Helpful session");
        feedback.setRating(5);

        when(sessionRepository.existsById("session-1")).thenReturn(true);
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));
        when(feedbackRepository.existsBySessionIdAndReviewerId("session-1", "candidate-1")).thenReturn(false);
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.findByInterviewerId("interviewer-1")).thenReturn(java.util.List.of(session));
        when(feedbackRepository.findAll()).thenReturn(java.util.List.of(feedback));
        when(userRepository.findById("interviewer-1")).thenReturn(Optional.of(interviewer));

        Feedback saved = service.submitFeedback(actor, feedback);

        assertEquals("INTERVIEWER_REVIEW", saved.getReviewType());
        assertEquals(true, saved.getPublicReview());
        assertEquals("interviewer-1", saved.getTargetUserId());
    }
}
