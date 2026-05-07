package com.interview.platform.service;

import com.interview.platform.model.User;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AccountIdentityService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,24}$");
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[._-]{2,}");
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 24;

    private final UserRepository userRepository;

    public AccountIdentityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String normalizeUsername(String value) {
        String username = value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (username.length() < MIN_USERNAME_LENGTH) {
            throw new IllegalArgumentException("Username must be at least 3 characters");
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            throw new IllegalArgumentException("Username must be 24 characters or fewer");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Only lowercase letters, numbers, dots, underscores, and hyphens allowed");
        }
        return username;
    }

    public String usernameKey(String value) {
        return normalizeUsername(value);
    }

    public String cleanDisplayName(String value) {
        String displayName = value == null ? null : value.trim().replaceAll("\\s+", " ");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        if (displayName.length() > 120) {
            throw new IllegalArgumentException("Display name must be 120 characters or fewer");
        }
        return displayName;
    }

    public boolean isUsernameAvailable(String username, String currentUserId) {
        String normalized = normalizeUsername(username);
        return findUsernameOwner(normalized)
                .map(user -> user.getId() != null && user.getId().equals(currentUserId))
                .orElse(true);
    }

    public void ensureUsernameAvailable(String username, String currentUserId) {
        if (!isUsernameAvailable(username, currentUserId)) {
            throw new IllegalArgumentException("Username already taken");
        }
    }

    public boolean ensureIdentity(User user) {
        if (user == null) {
            return false;
        }
        boolean changed = false;
        String originalUsername = user.getUsername();
        String originalDisplayName = user.getDisplayName();

        if (!user.hasDisplayName()) {
            user.setDisplayName(displayNameSeed(originalUsername, user.getEmail()));
            changed = true;
        }

        String normalizedUsername = validUsernameOrNull(originalUsername);
        if (normalizedUsername == null) {
            normalizedUsername = nextGeneratedUsername(user);
        }

        String normalizedKey = validUsernameOrNull(user.getUsernameKey());
        if (!normalizedUsername.equals(originalUsername) || !normalizedUsername.equals(normalizedKey)) {
            user.setUsername(normalizedUsername);
            changed = true;
        }
        return changed;
    }

    public String usernameForRegistration(String requestedUsername, String displayName, String email) {
        if (!isBlank(requestedUsername)) {
            String username = normalizeUsername(requestedUsername);
            ensureUsernameAvailable(username, null);
            return username;
        }
        return nextGeneratedUsername(displayName, email, null);
    }

    public boolean isValidUsername(String value) {
        return validUsernameOrNull(value) != null;
    }

    private Optional<User> findUsernameOwner(String normalizedUsername) {
        return userRepository.findByUsernameKey(normalizedUsername)
                .or(() -> userRepository.findFirstByUsernamePattern("^" + Pattern.quote(normalizedUsername) + "$"));
    }

    private String nextGeneratedUsername(User user) {
        return nextGeneratedUsername(user.getDisplayName(), user.getEmail(), user.getId());
    }

    private String nextGeneratedUsername(String displayName, String email, String currentUserId) {
        String base = generatedBase(displayName, email, currentUserId);
        for (int attempt = 0; attempt < 1000; attempt++) {
            String suffix = attempt == 0 ? "" : String.valueOf(attempt + 1);
            String candidate = withSuffix(base, suffix);
            if (isUsernameAvailable(candidate, currentUserId)) {
                return candidate;
            }
        }
        String fallback = currentUserId == null ? String.valueOf(System.currentTimeMillis()) : currentUserId;
        return withSuffix("user", digitsOnly(fallback));
    }

    private String generatedBase(String displayName, String email, String currentUserId) {
        String seed = !isBlank(displayName) ? displayName : emailLocalPart(email);
        String sanitized = sanitizeUsernameSeed(seed);
        if (sanitized.length() < MIN_USERNAME_LENGTH) {
            sanitized = sanitizeUsernameSeed(emailLocalPart(email));
        }
        if (sanitized.length() < MIN_USERNAME_LENGTH) {
            sanitized = "user" + digitsOnly(currentUserId == null ? "" : currentUserId);
        }
        if (sanitized.length() < MIN_USERNAME_LENGTH) {
            sanitized = "user";
        }
        return trimUsername(sanitized);
    }

    private String sanitizeUsernameSeed(String seed) {
        if (seed == null) {
            return "";
        }
        String ascii = Normalizer.normalize(seed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", ".")
                .replaceAll("[^a-z0-9._-]", "");
        ascii = SEPARATOR_PATTERN.matcher(ascii).replaceAll(".");
        ascii = ascii.replaceAll("^[._-]+|[._-]+$", "");
        return ascii;
    }

    private String withSuffix(String base, String suffix) {
        if (isBlank(suffix)) {
            return trimUsername(base);
        }
        String trimmedBase = trimUsername(base);
        int maxBaseLength = Math.max(1, MAX_USERNAME_LENGTH - suffix.length());
        if (trimmedBase.length() > maxBaseLength) {
            trimmedBase = trimmedBase.substring(0, maxBaseLength).replaceAll("[._-]+$", "");
        }
        return trimUsername(trimmedBase + suffix);
    }

    private String trimUsername(String value) {
        String username = value == null ? "" : value;
        if (username.length() > MAX_USERNAME_LENGTH) {
            username = username.substring(0, MAX_USERNAME_LENGTH);
        }
        username = username.replaceAll("[._-]+$", "");
        return username.length() < MIN_USERNAME_LENGTH ? "user" : username;
    }

    private String validUsernameOrNull(String value) {
        try {
            return normalizeUsername(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String displayNameSeed(String username, String email) {
        if (!isBlank(username)) {
            return username.trim().replaceAll("\\s+", " ");
        }
        String emailPart = emailLocalPart(email);
        return isBlank(emailPart) ? "InterviewPrep User" : emailPart;
    }

    private String emailLocalPart(String email) {
        if (isBlank(email)) {
            return "";
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
