package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.User;
import com.interview.platform.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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

    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success("Users fetched successfully", userService.getAllUsers()));
    }

    @GetMapping("/interviewers")
    public ResponseEntity<ApiResponse<List<User>>> getInterviewers(@RequestParam(required = false) String skill) {
        return ResponseEntity.ok(ApiResponse.success("Interviewers fetched successfully", userService.getInterviewers(skill)));
    }
}
