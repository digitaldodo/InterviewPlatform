package com.interview.platform.service;

import com.interview.platform.dto.InterviewerFilterOptions;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewerServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private AvailabilitySlotService availabilitySlotService;

    @Test
    void filterOptionsUseOnlyRealInterviewerProfileValues() {
        InterviewerService service = new InterviewerService(mongoTemplate, userRepository, sessionRepository, feedbackRepository, availabilitySlotService);
        User first = interviewer(List.of("Java", "Spring Boot, DSA"), "English, Hindi", "OpenAI");
        User second = interviewer(List.of("java", "React"), "Hindi", "Meta");
        first.setTimeZone("Asia/Kolkata");
        second.setTimeZone("UTC");
        first.setInterviewTopics(List.of("System Design", "Behavioral"));
        second.setInterviewTopics(List.of("React", "Frontend"));
        first.setSessionDurations(List.of(30, 45));
        second.setSessionDurations(List.of(60));

        when(mongoTemplate.find(any(Query.class), org.mockito.ArgumentMatchers.eq(User.class)))
                .thenReturn(List.of(first, second));

        InterviewerFilterOptions options = service.filterOptions();

        assertEquals(List.of("DSA", "Java", "React", "Spring Boot"), options.getExpertise());
        assertEquals(List.of("English", "Hindi"), options.getLanguages());
        assertEquals(List.of("Meta", "OpenAI"), options.getCompanies());
        assertEquals(List.of("Asia/Kolkata", "UTC"), options.getTimeZones());
        assertEquals(List.of("Behavioral", "Frontend", "React", "System Design"), options.getTopics());
        assertEquals(List.of(30, 45, 60), options.getSessionDurations());
    }

    private User interviewer(List<String> skills, String language, String company) {
        User user = new User();
        user.setRole("INTERVIEWER");
        user.setSkills(skills);
        user.setLanguage(language);
        user.setCompany(company);
        return user;
    }
}
