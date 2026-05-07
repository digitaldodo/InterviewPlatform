package com.interview.platform.service;

import com.interview.platform.dto.UserDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.User;
import com.interview.platform.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryImageService cloudinaryImageService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       CloudinaryImageService cloudinaryImageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.cloudinaryImageService = cloudinaryImageService;
    }

    public User register(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User details are required");
        }
        if (isBlank(user.getUsername()) || isBlank(user.getEmail()) || isBlank(user.getPassword())) {
            throw new IllegalArgumentException("Name, email, and password are required");
        }
        String email = user.getEmail().trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (user.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists. Please sign in instead.");
        }
        user.setUsername(user.getUsername().trim());
        user.setEmail(email);
        if (user.getRoles().isEmpty()) {
            user.setRole(isBlank(user.getRole()) ? "INTERVIEWEE" : user.getRole());
        }
        user.setPasswordHash(passwordEncoder.encode(user.getPassword()));
        user.setPassword(null);
        user.setCreatedAt(Instant.now());
        user.setIsVerified(true);
        return userRepository.save(user);
    }

    public Optional<User> getById(String id) {
        if (isBlank(id)) {
            return Optional.empty();
        }
        return userRepository.findById(id);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> loginUser(String email, String password) {
        if (isBlank(email) || isBlank(password)) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim().toLowerCase())
                .filter(user -> matchesPassword(user, password))
                .map(user -> {
                    user.setLastLogin(Instant.now());
                    return userRepository.save(user);
                });
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
        if (!isBlank(request.getName())) {
            user.setUsername(request.getName().trim());
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
        if (request.getPreferredDomains() != null) {
            user.setPreferredDomains(cleanList(request.getPreferredDomains()));
        }
        if (request.getExperienceLevel() != null) {
            user.setExperienceLevel(trimToNull(request.getExperienceLevel()));
        }
        if (request.getAvailability() != null) {
            user.setAvailability(cleanList(request.getAvailability()));
        }
        return userRepository.save(user);
    }

    public User uploadOwnAvatar(String userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setAvatarUrl(cloudinaryImageService.uploadProfileImage(userId, file));
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
}
