package com.liveseat.auth.controller;

import com.liveseat.auth.service.AuthService;
import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.exception.BusinessException;
import com.liveseat.common.security.UserHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                 @RequestParam(required = false) String userRole,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return ApiResponse.ok(authService.adminUsers(userRole, status, keyword, page, size));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<Map<String, Object>> changeStatus(@RequestHeader(UserHeaders.USER_ID) Long operatorId,
                                                         @RequestHeader(UserHeaders.USER_NAME) String operatorName,
                                                         @RequestHeader(UserHeaders.USER_ROLE) String role,
                                                         @PathVariable Long userId,
                                                         @Valid @RequestBody UserStatusRequest request) {
        requireAdmin(role);
        return ApiResponse.ok("账号状态已更新",
                authService.changeUserStatus(operatorId, operatorName, userId, request.status()));
    }

    @GetMapping("/{userId}")
    public ApiResponse<Map<String, Object>> detail(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                   @PathVariable Long userId) {
        requireAdmin(role);
        return ApiResponse.ok(authService.adminUserDetail(userId));
    }

    @GetMapping("/audits/list")
    public ApiResponse<Map<String, Object>> audits(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                   @RequestParam(required = false) String action,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return ApiResponse.ok(authService.audits(action, keyword, page, size));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) throw new BusinessException(403, "仅管理员可执行此操作");
    }

    public record UserStatusRequest(@NotBlank String status) {
    }
}
