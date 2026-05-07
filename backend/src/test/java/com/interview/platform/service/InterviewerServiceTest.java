package com.interview.platform.service;

import com.interview.platform.dto.InterviewerFilterOptions;
import com.interview.platform.model.User;
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
    private AvailabilitySlotService availabilitySlotService;

    @Test
    void filterOptionsUseOnlyRealInterviewerProfileValues() {
        InterviewerService service = new InterviewerService(mongoTemplate, userRepository, availabilitySlotService);
        User first = interviewer(List.of("Java", "Spring Boot, DSA"), "English, Hindi", "OpenAI");
        User second = interviewer(List.of("java", "React"), "Hindi", "Meta");

        when(mongoTemplate.find(any(Query.class), org.mockito.ArgumentMatchers.eq(User.class)))
                .thenReturn(List.of(first, second));

        InterviewerFilterOptions options = service.filterOptions();

        assertEquals(List.of("DSA", "Java", "React", "Spring Boot"), options.getExpertise());
        assertEquals(List.of("English", "Hindi"), options.getLanguages());
        assertEquals(List.of("Meta", "OpenAI"), options.getCompanies());
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
