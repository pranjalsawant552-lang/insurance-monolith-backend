package com.insurance.auth.controller;

import com.insurance.auth.dto.response.*;
import com.insurance.auth.entity.User;
import com.insurance.auth.exception.UserNotFoundException;
import com.insurance.auth.repository.AuthAuditLogRepository;
import com.insurance.auth.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin – User Management", description = "Admin-only user management endpoints")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class AdminUserController {

    private final UserRepository userRepository;
    private final AuthAuditLogRepository auditLogRepository;

    @GetMapping
    @Operation(summary = "List all users (paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<UserProfileResponse>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);
        Page<UserProfileResponse> users = userRepository.findAll(pageable)
                .map(UserProfileResponse::from);

        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(users)));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUser(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return ResponseEntity.ok(ApiResponse.success(UserProfileResponse.from(user)));
    }

    @PostMapping("/{userId}/lock")
    @Operation(summary = "Manually lock a user account")
    public ResponseEntity<ApiResponse<Void>> lockUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "Manual admin lock") String reason) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setLockedAt(ZonedDateTime.now());
        user.setLockedUntil(ZonedDateTime.now().plusYears(100)); // indefinite
        user.setLockReason(reason);
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success(null, "User locked."));
    }

    @PostMapping("/{userId}/unlock")
    @Operation(summary = "Admin force-unlock a user account")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.resetFailedAttempts();
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success(null, "User unlocked."));
    }

    @GetMapping("/{userId}/audit-log")
    @Operation(summary = "Get auth audit log for a user (paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<?>>> getUserAuditLog(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by("createdAt").descending());
        var logs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(logs)));
    }
}
