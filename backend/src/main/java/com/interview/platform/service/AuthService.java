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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
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
        user.setRole(request.getRole());
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
            throw new IllegalArgumentException("Email is already registered");
        }
        user.setUsername(trim(user.getUsername()));
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(user.getPassword()));
        user.setPassword(null);
        user.setCreatedAt(Instant.now());
        user.setIsVerified(verifiedByDefault);
        if (isBlank(user.getRole())) {
            user.setRole("INTERVIEWEE");
        }
        if (user.getSkills() == null) {
            user.setSkills(List.of());
        }
        return userRepository.save(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!matchesPassword(user, request.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!Boolean.TRUE.equals(user.getIsVerified())) {
            throw new UnauthorizedException("Please verify your email before signing in");
        }
        user.setLastLogin(Instant.now());
        userRepository.save(user);
        return authResponse(user);
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
        VerificationOtp record = new VerificationOtp();
        record.setEmail(normalized);
        record.setOtpHash(passwordEncoder.encode(otp));
        record.setCreatedAt(now);
        record.setLastSentAt(now);
        record.setExpiresAt(now.plusSeconds(600));
        verificationOtpRepository.save(record);
        emailService.sendVerificationOtp(normalized, otp);
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
            String token = tokenService.randomToken();
            PasswordResetToken record = new PasswordResetToken();
            record.setUserId(user.getId());
            record.setTokenHash(tokenService.sha256(token));
            record.setCreatedAt(Instant.now());
            record.setExpiresAt(Instant.now().plusSeconds(1800));
            passwordResetTokenRepository.save(record);
            emailService.sendPasswordReset(user.getEmail(), token);
        });
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
        User user = userRepository.findById(record.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPassword(null);
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
        String role = user.getRole() == null ? "INTERVIEWEE" : user.getRole().toUpperCase(Locale.ROOT);
        if (!role.equals("ADMIN") && !role.equals("INTERVIEWER") && !role.equals("INTERVIEWEE")) {
            throw new IllegalArgumentException("Role must be ADMIN, INTERVIEWER, or INTERVIEWEE");
        }
        user.setRole(role);
    }

    private String normalizeEmail(String email) {
        if (isBlank(email)) {
            throw new IllegalArgumentException("Valid email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
