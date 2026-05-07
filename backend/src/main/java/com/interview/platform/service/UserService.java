package com.interview.platform.service;

import com.interview.platform.dto.UserDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.InterviewReportRepository;
import com.interview.platform.repository.InterviewerAvailabilityRepository;
import com.interview.platform.repository.NotificationRepository;
import com.interview.platform.repository.PasswordResetTokenRepository;
import com.interview.platform.repository.RefreshTokenRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import com.interview.platform.repository.VerificationOtpRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryImageService cloudinaryImageService;
    private final InterviewerAvailabilityRepository availabilityRepository;
    private final SessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;
    private final InterviewReportRepository interviewReportRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final VerificationOtpRepository verificationOtpRepository;
    private final AccountIdentityService accountIdentityService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       CloudinaryImageService cloudinaryImageService,
                       InterviewerAvailabilityRepository availabilityRepository,
                       SessionRepository sessionRepository,
                       FeedbackRepository feedbackRepository,
                       InterviewReportRepository interviewReportRepository,
                       NotificationRepository notificationRepository,
                       NotificationService notificationService,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       VerificationOtpRepository verificationOtpRepository,
                       AccountIdentityService accountIdentityService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.cloudinaryImageService = cloudinaryImageService;
        this.availabilityRepository = availabilityRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.interviewReportRepository = interviewReportRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.verificationOtpRepository = verificationOtpRepository;
        this.accountIdentityService = accountIdentityService;
    }

    public Optional<User> getById(String id) {
        if (isBlank(id)) {
            return Optional.empty();
        }
        return userRepository.findById(id).map(this::ensureIdentitySaved);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll().stream().map(this::ensureIdentitySaved).toList();
    }

    public List<User> getInterviewers(String skill) {
        List<User> interviewers = userRepository.findByRole("INTERVIEWER");
        if (isBlank(skill)) {
            return interviewers;
        }
        String query = skill.trim().toLowerCase();
        return interviewers.stream()
                .filter(user -> user.getSkills() != null &&
                        user.getSkills().stream().anyMatch(s -> s != null && s.toLowerCase().contains(query)))
                .toList();
    }

    public User updateOwnProfile(String userId, UserDtos.ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        accountIdentityService.ensureIdentity(user);
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            String username = accountIdentityService.normalizeUsername(request.getUsername());
            accountIdentityService.ensureUsernameAvailable(username, user.getId());
            user.setUsername(username);
        }
        String displayName = !isBlank(request.getDisplayName()) ? request.getDisplayName() : request.getName();
        if (!isBlank(displayName)) {
            user.setDisplayName(accountIdentityService.cleanDisplayName(displayName));
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        }
        if (request.getBio() != null) {
            user.setBio(trimToNull(request.getBio()));
        }
        if (request.getSkills() != null) {
            user.setSkills(cleanList(request.getSkills()));
        }
        if (request.getLanguage() != null) {
            user.setLanguage(trimToNull(request.getLanguage()));
        }
        if (request.getTimeZone() != null) {
            user.setTimeZone(trimToNull(request.getTimeZone()));
        }
        if (request.getPreferredDomains() != null) {
            user.setPreferredDomains(cleanList(request.getPreferredDomains()));
        }
        if (request.getInterviewTopics() != null) {
            user.setInterviewTopics(cleanList(request.getInterviewTopics()));
        }
        if (request.getSessionDurations() != null) {
            user.setSessionDurations(request.getSessionDurations());
        }
        if (request.getExperienceLevel() != null) {
            user.setExperienceLevel(trimToNull(request.getExperienceLevel()));
        }
        if (request.getCompany() != null) {
            user.setCompany(trimToNull(request.getCompany()));
        }
        if (request.getCurrentRole() != null) {
            user.setCurrentRole(trimToNull(request.getCurrentRole()));
        }
        if (request.getYearsExperience() != null) {
            user.setYearsExperience(request.getYearsExperience());
        }
        if (request.getAvailability() != null) {
            user.setAvailability(cleanList(request.getAvailability()));
        }
        if (request.getAcceptingBookings() != null) {
            user.setAcceptingBookings(request.getAcceptingBookings());
        }
        if (request.getPublicProfileVisible() != null) {
            user.setPublicProfileVisible(request.getPublicProfileVisible());
        }
        return userRepository.save(user);
    }

    public boolean isUsernameAvailable(String username, String currentUserId) {
        return accountIdentityService.isUsernameAvailable(username, currentUserId);
    }

    public User addOwnRole(String userId, UserDtos.AddRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String role = normalizeRole(request == null ? null : request.getRole());
        if (role == null) {
            throw new IllegalArgumentException("Choose a valid role");
        }
        List<String> roles = new ArrayList<>(user.getRoles());
        boolean added = false;
        if (!roles.contains(role)) {
            roles.add(role);
            user.setRoles(roles);
            added = true;
        }
        if (isBlank(user.getActiveWorkspace()) || !user.getRoles().contains(user.getActiveWorkspace())) {
            user.setActiveWorkspace(role);
        }
        User saved = userRepository.save(user);
        if (added) {
            notificationService.create(
                    saved.getId(),
                    "ROLE_UPDATED",
                    "New workspace added",
                    "You can now switch into the " + role.toLowerCase(Locale.ROOT) + " workspace.",
                    java.util.Map.of("role", role)
            );
        }
        return saved;
    }

    public void deleteOwnAccount(String userId, UserDtos.DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String confirmation = request == null ? null : request.getConfirmation();
        if (confirmation == null || !"DELETE".equalsIgnoreCase(confirmation.trim())) {
            throw new IllegalArgumentException("Type DELETE to confirm account deletion");
        }
        String password = request.getPassword();
        boolean hasPassword = !isBlank(user.getPasswordHash()) || !isBlank(user.getPassword());
        if (hasPassword && (isBlank(password) || !matchesPassword(user, password))) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        List<Session> sessions = sessionRepository.findByInterviewerIdOrCandidateId(userId, userId);
        for (Session session : sessions) {
            if (!isBlank(session.getId())) {
                feedbackRepository.deleteBySessionId(session.getId());
                interviewReportRepository.deleteBySessionId(session.getId());
            }
        }
        sessionRepository.deleteAll(sessions);
        availabilityRepository.deleteByInterviewerId(userId);
        notificationRepository.deleteByUserId(userId);
        refreshTokenRepository.deleteByUserId(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
        if (!isBlank(user.getEmail())) {
            verificationOtpRepository.deleteByEmail(user.getEmail());
        }
        removeFavoriteReferences(userId);
        userRepository.delete(user);
    }

    public User uploadOwnAvatar(String userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setAvatarUrl(cloudinaryImageService.uploadProfileImage(userId, file));
        return userRepository.save(user);
    }

    public User uploadOwnResume(String userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CloudinaryImageService.UploadedAsset asset = cloudinaryImageService.uploadResumeDocument(userId, file);
        user.setResumeUrl(asset.url());
        user.setResumeFileName(asset.fileName());
        user.setResumeContentType(asset.contentType());
        user.setResumeUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    public User removeOwnResume(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setResumeUrl(null);
        user.setResumeFileName(null);
        user.setResumeContentType(null);
        user.setResumeUpdatedAt(null);
        return userRepository.save(user);
    }

    public void changeOwnPassword(String userId, UserDtos.ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (isBlank(request.getCurrentPassword()) || !matchesPassword(user, request.getCurrentPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (isBlank(request.getNewPassword()) || request.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPassword(null);
        userRepository.save(user);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("INTERVIEWER") || normalized.equals("INTERVIEWEE") ? normalized : null;
    }

    private void removeFavoriteReferences(String deletedUserId) {
        userRepository.findAll().forEach(user -> {
            List<String> favorites = user.getFavoriteInterviewerIds();
            if (favorites == null || !favorites.contains(deletedUserId)) {
                return;
            }
            user.setFavoriteInterviewerIds(favorites.stream()
                    .filter(id -> !deletedUserId.equals(id))
                    .toList());
            userRepository.save(user);
        });
    }

    private boolean matchesPassword(User user, String rawPassword) {
        if (!isBlank(user.getPasswordHash()) && passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            return true;
        }
        if (!isBlank(user.getPassword()) && rawPassword.equals(user.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setPassword(null);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private List<String> cleanList(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private User ensureIdentitySaved(User user) {
        if (accountIdentityService.ensureIdentity(user)) {
            return userRepository.save(user);
        }
        return user;
    }
}
