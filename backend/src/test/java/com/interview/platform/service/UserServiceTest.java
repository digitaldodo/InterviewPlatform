package com.interview.platform.service;

import com.interview.platform.dto.UserDtos;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.InterviewerAvailabilityRepository;
import com.interview.platform.repository.NotificationRepository;
import com.interview.platform.repository.PasswordResetTokenRepository;
import com.interview.platform.repository.RefreshTokenRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import com.interview.platform.repository.VerificationOtpRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CloudinaryImageService cloudinaryImageService;

    @Mock
    private InterviewerAvailabilityRepository availabilityRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private VerificationOtpRepository verificationOtpRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void uploadOwnAvatarStoresCloudinaryUrl() {
        User user = new User();
        user.setId("user-123");
        user.setAvatarUrl(null);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "avatar".getBytes()
        );

        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));
        when(cloudinaryImageService.uploadProfileImage("user-123", file)).thenReturn("https://cdn.example.com/avatar.png");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.uploadOwnAvatar("user-123", file);

        assertEquals("https://cdn.example.com/avatar.png", updated.getAvatarUrl());
        verify(userRepository).save(user);
    }

    @Test
    void updateOwnProfileRejectsCaseInsensitiveDuplicateUsername() {
        User current = new User();
        current.setId("user-123");
        current.setUsername("Current User");

        User duplicate = new User();
        duplicate.setId("user-456");
        duplicate.setUsername("Ansh");

        UserDtos.ProfileUpdateRequest request = new UserDtos.ProfileUpdateRequest();
        request.setName(" ANSH ");

        when(userRepository.findById("user-123")).thenReturn(Optional.of(current));
        when(userRepository.findByUsernameKey("ansh")).thenReturn(Optional.of(duplicate));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.updateOwnProfile("user-123", request));

        assertEquals("Username already taken", ex.getMessage());
    }
}
