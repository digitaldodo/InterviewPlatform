package com.interview.platform.service;

import com.interview.platform.model.User;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
            throw new IllegalArgumentException("Email is already registered");
        }
        user.setUsername(user.getUsername().trim());
        user.setEmail(email);
        if (isBlank(user.getRole())) {
            user.setRole("INTERVIEWEE");
        }
        if (!"INTERVIEWER".equals(user.getRole()) && !"INTERVIEWEE".equals(user.getRole())) {
            throw new IllegalArgumentException("Role must be INTERVIEWER or INTERVIEWEE");
        }
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
                .filter(user -> password.equals(user.getPassword()));
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
