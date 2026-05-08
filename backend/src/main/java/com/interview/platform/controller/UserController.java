package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.AuthDtos;
import com.interview.platform.dto.UserDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.User;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.AuthService;
import com.interview.platform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Registration started. Verify your email with the OTP.",
                authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable String id, Authentication authentication) {
        User actor = currentUser(authentication);
        if (!actor.hasRole("ADMIN") && !actor.getId().equals(id)) {
            throw new UnauthorizedException("You can only access your own profile");
        }
        User user = userService.getById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(ApiResponse.success("User fetched successfully", user));
    }

    @GetMapping("/me/profile")
    public ResponseEntity<ApiResponse<User>> getOwnProfile(Authentication authentication) {
        User user = userService.getById(currentUser(authentication).getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", user));
    }

    @GetMapping("/username-availability")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> usernameAvailability(@RequestParam String username,
                                                                                  @RequestParam(required = false) String currentUserId,
                                                                                  Authentication authentication) {
        String effectiveCurrentUserId = currentUserId;
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            effectiveCurrentUserId = principal.getUser().getId();
        }
        boolean available = userService.isUsernameAvailable(username, effectiveCurrentUserId);
        return ResponseEntity.ok(ApiResponse.success("Username availability checked", Map.of("available", available)));
    }

    @PutMapping("/me/profile")
    public ResponseEntity<ApiResponse<User>> updateOwnProfile(Authentication authentication,
                                                              @Valid @RequestBody UserDtos.ProfileUpdateRequest request) {
        User updated = userService.updateOwnProfile(currentUser(authentication).getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", updated));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<User>> uploadOwnAvatar(Authentication authentication,
                                                             @RequestParam("file") MultipartFile file) {
        User updated = userService.uploadOwnAvatar(currentUser(authentication).getId(), file);
        return ResponseEntity.ok(ApiResponse.success("Profile image uploaded successfully", updated));
    }

    @PostMapping(value = "/me/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<User>> uploadOwnResume(Authentication authentication,
                                                             @RequestParam("file") MultipartFile file) {
        User updated = userService.uploadOwnResume(currentUser(authentication).getId(), file);
        return ResponseEntity.ok(ApiResponse.success("Resume uploaded successfully", updated));
    }

    @DeleteMapping("/me/resume")
    public ResponseEntity<ApiResponse<User>> removeOwnResume(Authentication authentication) {
        User updated = userService.removeOwnResume(currentUser(authentication).getId());
        return ResponseEntity.ok(ApiResponse.success("Resume removed successfully", updated));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changeOwnPassword(Authentication authentication,
                                                               @Valid @RequestBody UserDtos.ChangePasswordRequest request) {
        userService.changeOwnPassword(currentUser(authentication).getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully", null));
    }

    @PostMapping("/me/roles")
    public ResponseEntity<ApiResponse<User>> addOwnRole(Authentication authentication,
                                                        @Valid @RequestBody UserDtos.AddRoleRequest request) {
        User updated = userService.addOwnRole(currentUser(authentication).getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Role added successfully", updated));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteOwnAccount(Authentication authentication,
                                                             @Valid @RequestBody(required = false) UserDtos.DeleteAccountRequest request) {
        userService.deleteOwnAccount(currentUser(authentication).getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully", null));
    }

    @PostMapping("/me/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendOwnOtp(Authentication authentication) {
        authService.sendOtp(currentUser(authentication).getEmail());
        return ResponseEntity.ok(ApiResponse.success("Verification OTP sent", null));
    }

    @PostMapping("/me/verify-otp")
    public ResponseEntity<ApiResponse<User>> verifyOwnOtp(Authentication authentication,
                                                          @Valid @RequestBody AuthDtos.VerifyOtpRequest request) {
        User user = currentUser(authentication);
        if (!user.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new UnauthorizedException("You can only verify your own email");
        }
        authService.verifyOtp(user.getEmail(), request.getOtp());
        User updated = userService.getById(user.getId()).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(ApiResponse.success("Email verified", updated));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success("Users fetched successfully", userService.getAllUsers()));
    }

    @GetMapping("/interviewers")
    public ResponseEntity<ApiResponse<List<User>>> getInterviewers(@RequestParam(required = false) String skill) {
        return ResponseEntity.ok(ApiResponse.success("Interviewers fetched successfully", userService.getInterviewers(skill)));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }
}
