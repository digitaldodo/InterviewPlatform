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
    public ResponseEntity<ApiResponse<User>> register(@RequestBody User user) {
        return ResponseEntity.ok(ApiResponse.success("User registered successfully", userService.register(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<User>> login(@RequestBody Map<String, String> credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("Email and password are required");
        }
        String email = credentials.get("email");
        String password = credentials.get("password");
        User user = userService.loginUser(email, password)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        return ResponseEntity.ok(ApiResponse.success("Login successful", user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable String id) {
        User user = userService.getById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(ApiResponse.success("User fetched successfully", user));
    }

    @GetMapping("/me/profile")
    public ResponseEntity<ApiResponse<User>> getOwnProfile(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", currentUser(authentication)));
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

    @PostMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changeOwnPassword(Authentication authentication,
                                                               @Valid @RequestBody UserDtos.ChangePasswordRequest request) {
        userService.changeOwnPassword(currentUser(authentication).getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully", null));
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
