package com.liveseat.auth.controller;

import com.liveseat.auth.service.AuthService;
import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.security.UserHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("注册成功", authService.register(
                request.username(), request.phone(), request.password(), request.nickname()));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("登录成功", authService.login(request.account(), request.password()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(authorization);
        return ApiResponse.ok("已退出登录", null);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestHeader(UserHeaders.USER_ID) Long userId) {
        return ApiResponse.ok(authService.currentUser(userId));
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                          @Valid @RequestBody ProfileRequest request) {
        return ApiResponse.ok("个人资料已更新", authService.updateProfile(userId, request.nickname(), request.phone()));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                            @Valid @RequestBody PasswordRequest request) {
        authService.changePassword(userId, request.oldPassword(), request.newPassword());
        return ApiResponse.ok("密码已更新，请重新登录", null);
    }

    public record LoginRequest(@NotBlank String account, @NotBlank @Size(min = 6, max = 32) String password) {
    }

    public record RegisterRequest(@NotBlank @Size(min = 3, max = 30) String username,
                                  @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确手机号") String phone,
                                  @NotBlank @Size(min = 6, max = 32) String password,
                                  @Size(max = 30) String nickname) {
    }

    public record ProfileRequest(@Size(max = 30) String nickname,
                                 @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确手机号") String phone) {}
    public record PasswordRequest(@NotBlank String oldPassword,
                                  @NotBlank @Size(min = 6, max = 32) String newPassword) {}

}
