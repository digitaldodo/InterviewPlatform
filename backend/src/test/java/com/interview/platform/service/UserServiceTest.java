package com.interview.platform.service;

import com.interview.platform.model.User;
import com.interview.platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
