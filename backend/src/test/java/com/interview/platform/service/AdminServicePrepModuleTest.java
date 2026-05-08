package com.interview.platform.service;

import com.interview.platform.dto.AdminDtos;
import com.interview.platform.dto.PageResponse;
import com.interview.platform.model.PrepModule;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.ModerationAuditLogRepository;
import com.interview.platform.repository.NotificationRepository;
import com.interview.platform.repository.PlatformNoticeRepository;
import com.interview.platform.repository.PrepModuleRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserReportRepository;
import com.interview.platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServicePrepModuleTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private UserReportRepository userReportRepository;
    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private ModerationAuditService moderationAuditService;
    @Mock
    private ModerationAuditLogRepository moderationAuditLogRepository;
    @Mock
    private TrustSignalService trustSignalService;
    @Mock
    private CacheInvalidationService cacheInvalidationService;
    @Mock
    private PrepModuleRepository prepModuleRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private PlatformNoticeRepository platformNoticeRepository;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                userRepository,
                sessionRepository,
                userReportRepository,
                feedbackRepository,
                moderationAuditService,
                moderationAuditLogRepository,
                trustSignalService,
                cacheInvalidationService,
                prepModuleRepository,
                notificationRepository,
                platformNoticeRepository,
                "",
                "no-reply@interviewprep.local"
        );
    }

    @Test
    void prepModulesReturnsEmptyListWhenRepositoryIsEmpty() {
        when(prepModuleRepository.findAll()).thenReturn(List.of());

        List<AdminDtos.PrepModuleItem> modules = adminService.prepModules();

        assertNotNull(modules);
        assertEquals(0, modules.size());
    }

    @Test
    void prepModulesIgnoresMalformedResourcesAndDefaultsMissingFields() {
        PrepModule module = new PrepModule();
        module.setTitle(null);
        module.setDescription(null);
        module.setCategory(null);
        module.setDifficulty(null);
        module.setVisibilityStatus("unknown");
        PrepModule.ResourceLink brokenResource = new PrepModule.ResourceLink();
        brokenResource.setLabel("Broken");
        brokenResource.setUrl(null);
        PrepModule.ResourceLink validResource = new PrepModule.ResourceLink();
        validResource.setLabel(null);
        validResource.setUrl("https://example.com/guide");
        List<PrepModule.ResourceLink> resources = new ArrayList<>();
        resources.add(null);
        resources.add(brokenResource);
        resources.add(validResource);
        module.setResources(resources);
        when(prepModuleRepository.findAll()).thenReturn(List.of(module));

        AdminDtos.PrepModuleItem item = adminService.prepModules().get(0);

        assertEquals("Untitled module", item.title());
        assertEquals("Uncategorized", item.category());
        assertEquals("Foundational", item.difficulty());
        assertEquals("DRAFT", item.visibilityStatus());
        assertEquals(1, item.resources().size());
        assertEquals("https://example.com/guide", item.resources().get(0).label());
    }

    @Test
    void updatePrepModuleAppliesPartialPayloadWithoutClearingExistingFields() {
        PrepModule existing = new PrepModule();
        existing.setTitle("System design");
        existing.setDescription("Practice scalable architecture.");
        existing.setCategory("System Design");
        existing.setDifficulty("Intermediate");
        existing.setVisibilityStatus("DRAFT");
        when(prepModuleRepository.findById("module-1")).thenReturn(Optional.of(existing));
        when(prepModuleRepository.save(any(PrepModule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDtos.PrepModuleRequest request = new AdminDtos.PrepModuleRequest();
        request.setVisibilityStatus("PUBLISHED");

        AdminDtos.PrepModuleItem item = adminService.updatePrepModule("module-1", request);

        ArgumentCaptor<PrepModule> captor = ArgumentCaptor.forClass(PrepModule.class);
        org.mockito.Mockito.verify(prepModuleRepository).save(captor.capture());
        assertEquals("System design", captor.getValue().getTitle());
        assertEquals("Practice scalable architecture.", captor.getValue().getDescription());
        assertEquals("PUBLISHED", item.visibilityStatus());
        assertNotNull(captor.getValue().getUpdatedAt());
        assertFalse(item.createdAt() != null && item.createdAt().isBlank());
    }

    @Test
    void operationsReturnsSafeEmptyShapeWhenDataIsMissing() {
        when(notificationRepository.findAll()).thenReturn(List.of());
        when(platformNoticeRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of());

        AdminDtos.AdminOpsResponse response = adminService.operations();

        assertNotNull(response);
        assertEquals(0, response.totalNotifications());
        assertEquals(0, response.unreadNotifications());
        assertEquals(List.of(), response.platformNotices());
        assertEquals(List.of(), response.activeNotices());
        assertEquals(List.of(), response.recentNotifications());
        assertFalse(response.emailTemplates().isEmpty());
    }

    @Test
    void operationsDegradesWhenAggregationSourcesFail() {
        when(notificationRepository.findAll()).thenThrow(new RuntimeException("notification collection unavailable"));
        when(platformNoticeRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(null);

        AdminDtos.AdminOpsResponse response = adminService.operations();

        assertNotNull(response);
        assertEquals(0, response.totalNotifications());
        assertEquals(0, response.unreadNotifications());
        assertEquals(List.of(), response.platformNotices());
        assertEquals(List.of(), response.activeNotices());
        assertEquals(List.of(), response.recentNotifications());
    }

    @Test
    void overviewAndQueuesReturnSafeEmptyShapesForEmptyDatabase() {
        when(userRepository.findAll()).thenReturn(List.of());
        when(sessionRepository.findAll()).thenReturn(List.of());
        when(feedbackRepository.findAll()).thenReturn(List.of());
        when(userReportRepository.findAll()).thenReturn(List.of());
        when(moderationAuditLogRepository.findAll()).thenReturn(List.of());
        when(feedbackRepository.findByReviewTypeOrderByCreatedAtDesc("INTERVIEWER_REVIEW")).thenReturn(List.of());

        AdminDtos.OverviewResponse overview = adminService.overview();
        PageResponse<AdminDtos.AdminUserItem> users = adminService.users(null, null, null, null, null, null, null, null, null);
        PageResponse<AdminDtos.AdminSessionItem> sessions = adminService.sessions(null, null, null, null, null, null, null, null, null);
        PageResponse<AdminDtos.AdminReportItem> reports = adminService.reports(null, null, null, null, null, null, null);
        PageResponse<AdminDtos.ReviewQueueItem> reviews = adminService.reviews(null, null, null, null, null, null, null, null);
        AdminDtos.TrustDashboardResponse trust = adminService.trustDashboard();

        assertEquals(0, overview.totalUsers());
        assertEquals(0, overview.totalInterviewees());
        assertEquals(0, overview.activeMeetings());
        assertEquals(0, overview.upcomingInterviews());
        assertEquals(0, overview.reportsPendingReview());
        assertEquals(0, users.getTotal());
        assertEquals(0, sessions.getTotal());
        assertEquals(0, reports.getTotal());
        assertEquals(0, reviews.getTotal());
        assertEquals(0, trust.openReportCount());
    }
}
