package com.interview.platform.service;

import com.interview.platform.dto.AuthDtos;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.PasswordResetToken;
import com.interview.platform.model.RefreshToken;
import com.interview.platform.model.User;
import com.interview.platform.model.VerificationOtp;
import com.interview.platform.repository.PasswordResetTokenRepository;
import com.interview.platform.repository.RefreshTokenRepository;
import com.interview.platform.repository.UserRepository;
import com.interview.platform.repository.VerificationOtpRepository;
import com.interview.platform.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationOtpRepository verificationOtpRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final long refreshTokenDays;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            VerificationOtpRepository verificationOtpRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenService tokenService,
            EmailService emailService,
            @Value("${app.jwt.refresh-token-days}") long refreshTokenDays
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.verificationOtpRepository = verificationOtpRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.refreshTokenDays = refreshTokenDays;
    }

    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        User user = new User();
        user.setUsername(trim(request.getName()));
        user.setEmail(normalizeEmail(request.getEmail()));
        user.setPassword(request.getPassword());
        user.setRoles(normalizeRoles(request.getRoles(), request.getRole()));
        user.setSkills(request.getSkills());
        user.setCompany(trim(request.getCompany()));
        user.setCurrentRole(trim(request.getCurrentRole()));
        user.setBio(trim(request.getBio()));
        user.setLanguage(trim(request.getLanguage()));
        user.setYearsExperience(request.getYearsExperience());
        User created = registerUser(user, false);
        sendOtp(created.getEmail());
        return authResponse(created);
    }

    public User registerUser(User user, boolean verifiedByDefault) {
        validateNewUser(user);
        String email = normalizeEmail(user.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists. Please sign in instead.");
        }
        String username = cleanUsername(user.getUsername());
        ensureUsernameAvailable(username);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(user.getPassword()));
        user.setPassword(null);
        user.setCreatedAt(Instant.now());
        user.setIsVerified(verifiedByDefault);
        if (user.getRoles().isEmpty()) {
            user.setRoles(List.of("INTERVIEWEE"));
        }
        user.setActiveWorkspace(user.getActiveWorkspace());
        if (user.getSkills() == null) {
            user.setSkills(List.of());
        }
        return userRepository.save(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String identifier = loginIdentifier(request);
        User user = findLoginUser(identifier)
                .orElseThrow(() -> new UnauthorizedException("Invalid username/email or password"));
        if (!matchesPassword(user, request.getPassword())) {
            throw new UnauthorizedException("Invalid username/email or password");
        }
        if (!Boolean.TRUE.equals(user.getIsVerified()) && user.getCreatedAt() == null) {
            user.setIsVerified(true);
        }
        if (!Boolean.TRUE.equals(user.getIsVerified())) {
            throw new UnauthorizedException("Please verify your email before signing in");
        }
        user.setLastLogin(Instant.now());
        userRepository.save(user);
        return authResponse(user);
    }

    private String loginIdentifier(AuthDtos.LoginRequest request) {
        String value = trim(request.getIdentifier());
        if (isBlank(value)) {
            value = trim(request.getEmail());
        }
        if (isBlank(value)) {
            throw new IllegalArgumentException("Username or email is required");
        }
        return value;
    }

    private Optional<User> findLoginUser(String identifier) {
        if (looksLikeEmail(identifier)) {
            return userRepository.findByEmail(normalizeEmail(identifier));
        }
        String username = cleanUsername(identifier);
        return userRepository.findByUsernameKey(usernameKey(username))
                .or(() -> userRepository.findFirstByUsernamePattern("^" + Pattern.quote(username) + "$"));
    }

    public AuthDtos.AuthResponse refresh(String refreshToken) {
        String hash = tokenService.sha256(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new UnauthorizedException("Refresh token expired");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        return authResponse(user);
    }

    public void logout(String refreshToken) {
        if (isBlank(refreshToken)) return;
        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenService.sha256(refreshToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    public void sendOtp(String email) {
        String normalized = normalizeEmail(email);
        VerificationOtp recent = verificationOtpRepository.findTopByEmailOrderByCreatedAtDesc(normalized).orElse(null);
        Instant now = Instant.now();
        if (recent != null && recent.getLastSentAt() != null && recent.getLastSentAt().plusSeconds(60).isAfter(now)) {
            throw new IllegalArgumentException("Please wait before requesting another OTP");
        }
        String otp = tokenService.otp();
        log.info("Generated verification OTP for {}", maskEmail(normalized));
        VerificationOtp record = new VerificationOtp();
        record.setEmail(normalized);
        record.setOtpHash(passwordEncoder.encode(otp));
        record.setCreatedAt(now);
        record.setLastSentAt(now);
        record.setExpiresAt(now.plusSeconds(600));
        verificationOtpRepository.save(record);
        log.info("Stored verification OTP metadata for {}", maskEmail(normalized));
        emailService.sendVerificationOtp(normalized, otp);
        log.info("Verification OTP email accepted by SendGrid pipeline for {}", maskEmail(normalized));
    }

    public void verifyOtp(String email, String otp) {
        String normalized = normalizeEmail(email);
        VerificationOtp record = verificationOtpRepository.findTopByEmailOrderByCreatedAtDesc(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Verification code not found"));
        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Verification code expired");
        }
        if (record.getAttempts() >= 5) {
            throw new IllegalArgumentException("Too many verification attempts");
        }
        if (!passwordEncoder.matches(otp, record.getOtpHash())) {
            record.setAttempts(record.getAttempts() + 1);
            verificationOtpRepository.save(record);
            throw new IllegalArgumentException("Invalid verification code");
        }
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setIsVerified(true);
        userRepository.save(user);
        verificationOtpRepository.deleteByEmail(normalized);
    }

    public void forgotPassword(String email) {
        userRepository.findByEmail(normalizeEmail(email)).ifPresent(user -> {
            PasswordResetToken recent = passwordResetTokenRepository
                    .findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                    .orElse(null);
            Instant now = Instant.now();
            if (recent != null && recent.getLastSentAt() != null && recent.getLastSentAt().plusSeconds(60).isAfter(now)) {
                throw new IllegalArgumentException("Please wait before requesting another reset OTP");
            }
            String otp = tokenService.otp();
            log.info("Generated password reset OTP for {}", maskEmail(user.getEmail()));
            PasswordResetToken record = new PasswordResetToken();
            record.setUserId(user.getId());
            record.setOtpHash(passwordEncoder.encode(otp));
            record.setCreatedAt(now);
            record.setLastSentAt(now);
            record.setExpiresAt(now.plusSeconds(600));
            passwordResetTokenRepository.save(record);
            log.info("Stored password reset OTP metadata for {}", maskEmail(user.getEmail()));
            emailService.sendPasswordResetOtp(user.getEmail(), otp);
            log.info("Password reset OTP email accepted by SendGrid pipeline for {}", maskEmail(user.getEmail()));
        });
    }

    public String verifyResetOtp(String email, String otp) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset code"));
        PasswordResetToken record = passwordResetTokenRepository
                .findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Reset code not found"));
        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Reset code expired");
        }
        if (record.getAttempts() >= 5) {
            throw new IllegalArgumentException("Too many reset attempts");
        }
        if (record.getOtpHash() == null || !passwordEncoder.matches(otp, record.getOtpHash())) {
            record.setAttempts(record.getAttempts() + 1);
            passwordResetTokenRepository.save(record);
            throw new IllegalArgumentException("Invalid reset code");
        }
        String resetToken = tokenService.randomToken();
        record.setTokenHash(tokenService.sha256(resetToken));
        record.setVerified(true);
        record.setExpiresAt(Instant.now().plusSeconds(600));
        passwordResetTokenRepository.save(record);
        return resetToken;
    }

    public void resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        PasswordResetToken record = passwordResetTokenRepository.findByTokenHashAndUsedFalse(tokenService.sha256(token))
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));
        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Reset token expired");
        }
        if (!record.isVerified()) {
            throw new IllegalArgumentException("Reset code must be verified first");
        }
        User user = userRepository.findById(record.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPassword(null);
        user.setIsVerified(true);
        userRepository.save(user);
        record.setUsed(true);
        passwordResetTokenRepository.save(record);
    }

    private AuthDtos.AuthResponse authResponse(User user) {
        String refreshToken = tokenService.randomToken();
        RefreshToken record = new RefreshToken();
        record.setUserId(user.getId());
        record.setTokenHash(tokenService.sha256(refreshToken));
        record.setCreatedAt(Instant.now());
        record.setExpiresAt(Instant.now().plusSeconds(refreshTokenDays * 24 * 60 * 60));
        refreshTokenRepository.save(record);
        return new AuthDtos.AuthResponse(user, jwtService.generateAccessToken(user), refreshToken);
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

    private void validateNewUser(User user) {
        if (user == null || isBlank(user.getUsername()) || isBlank(user.getEmail()) || isBlank(user.getPassword())) {
            throw new IllegalArgumentException("Name, email, and password are required");
        }
        if (user.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        user.setRoles(normalizeRoles(user.getRoles(), user.getRole()));
    }

    private void ensureUsernameAvailable(String username) {
        String key = usernameKey(username);
        if (userRepository.existsByUsernameKey(key)) {
            throw new IllegalArgumentException("Username already taken");
        }
        userRepository.findFirstByUsernamePattern("^" + Pattern.quote(username) + "$")
                .ifPresent(match -> {
                    throw new IllegalArgumentException("Username already taken");
                });
    }

    private List<String> normalizeRoles(List<String> roles, String fallbackRole) {
        List<String> source = roles == null || roles.isEmpty() ? List.of(isBlank(fallbackRole) ? "INTERVIEWEE" : fallbackRole) : roles;
        List<String> normalized = source.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> role.equals("INTERVIEWER") || role.equals("INTERVIEWEE"))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Select at least one role");
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        if (isBlank(email)) {
            throw new IllegalArgumentException("Valid email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean looksLikeEmail(String value) {
        return value != null && value.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanUsername(String value) {
        String username = trim(value);
        if (isBlank(username)) {
            throw new IllegalArgumentException("Username is required");
        }
        return username.replaceAll("\\s+", " ");
    }

    private String usernameKey(String value) {
        return cleanUsername(value).toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "unknown";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String masked = name.length() <= 2 ? name.charAt(0) + "*" : name.substring(0, 2) + "***";
        return masked + "@" + parts[1];
    }
}
