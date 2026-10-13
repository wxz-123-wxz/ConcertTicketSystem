package com.liveseat.auth.service;

import com.liveseat.auth.client.OrderClient;
import com.liveseat.auth.client.PaymentClient;
import com.liveseat.auth.domain.User;
import com.liveseat.auth.mapper.UserMapper;
import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.exception.BusinessException;
import com.liveseat.common.security.JwtPayload;
import com.liveseat.common.security.JwtSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(2);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final String jwtSecret;
    private final OrderClient orderClient;
    private final PaymentClient paymentClient;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       StringRedisTemplate redisTemplate,
                       @Value("${app.jwt.secret}") String jwtSecret,
                       OrderClient orderClient, PaymentClient paymentClient) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.jwtSecret = jwtSecret;
        this.orderClient = orderClient;
        this.paymentClient = paymentClient;
    }

    @Transactional
    public Map<String, Object> register(String username, String phone, String password, String nickname) {
        if (userMapper.countExisting(username, phone) > 0) {
            throw new BusinessException("用户名或手机号已被使用");
        }
        User user = new User();
        user.setUsername(username);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        user.setRole("USER");
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return issueToken(user);
    }

    public Map<String, Object> login(String account, String password) {
        User user = userMapper.findByAccount(account);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(401, "账号或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }
        return issueToken(user);
    }

    public void logout(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            redisTemplate.delete("login:token:" + authorization.substring(7));
        }
    }

    public Map<String, Object> currentUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return publicUser(user);
    }

    @Transactional
    public Map<String, Object> updateProfile(Long userId, String nickname, String phone) {
        User user = userMapper.findById(userId);
        if (user == null) throw new BusinessException(404, "用户不存在");
        String normalizedNickname = nickname == null || nickname.isBlank() ? user.getUsername() : nickname.trim();
        if (normalizedNickname.length() > 30) throw new BusinessException("昵称不能超过 30 个字符");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) throw new BusinessException("请输入正确手机号");
        if (userMapper.countPhoneExcept(phone, userId) > 0) throw new BusinessException("手机号已被其他账号使用");
        userMapper.updateProfile(userId, normalizedNickname, phone);
        user.setNickname(normalizedNickname);
        user.setPhone(phone);
        return publicUser(user);
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.findById(userId);
        if (user == null) throw new BusinessException(404, "用户不存在");
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) throw new BusinessException("当前密码不正确");
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 32) {
            throw new BusinessException("新密码长度需要在 6–32 个字符之间");
        }
        if (oldPassword.equals(newPassword)) throw new BusinessException("新密码不能与当前密码相同");
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
        invalidateSessions(userId);
    }

    public Map<String, Object> adminUsers(String role, String status, String keyword, int page, int size) {
        String normalizedRole = normalizeFilter(role);
        String normalizedStatus = normalizeFilter(status);
        if (normalizedRole != null && !List.of("USER", "ADMIN").contains(normalizedRole)) {
            throw new BusinessException("不支持的用户角色筛选");
        }
        if (normalizedStatus != null && !List.of("ACTIVE", "DISABLED").contains(normalizedStatus)) {
            throw new BusinessException("不支持的账号状态筛选");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normalizedKeyword = normalizeFilter(keyword);
        List<Map<String, Object>> items = userMapper.findAdminUsers(normalizedRole, normalizedStatus,
                        normalizedKeyword, (safePage - 1) * safeSize, safeSize)
                .stream().map(this::adminUser).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", userMapper.countAdminUsers(normalizedRole, normalizedStatus, normalizedKeyword));
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    @Transactional
    public Map<String, Object> changeUserStatus(Long operatorId, String operatorName, Long userId, String status) {
        if (operatorId.equals(userId)) {
            throw new BusinessException("不能修改当前登录管理员的账号状态");
        }
        if (!List.of("ACTIVE", "DISABLED").contains(status)) {
            throw new BusinessException("不支持的账号状态");
        }
        User target = userMapper.findById(userId);
        if (target == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!"USER".equals(target.getRole())) {
            throw new BusinessException(403, "管理员账号受保护，不能在此修改");
        }
        if (!status.equals(target.getStatus())) {
            if (userMapper.updateUserStatus(userId, status) == 0) {
                throw new BusinessException(409, "账号状态更新失败，请刷新后重试");
            }
            target.setStatus(status);
        }
        if ("DISABLED".equals(status)) {
            invalidateSessions(userId);
        }
        userMapper.insertAudit(operatorId, operatorName, "USER_STATUS", "USER", userId.toString(),
                "账号 " + target.getUsername() + " 状态修改为 " + status);
        return adminUser(target);
    }

    public Map<String, Object> adminUserDetail(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) throw new BusinessException(404, "用户不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", adminUser(user));
        result.put("orders", requireData(orderClient.userSummary("ADMIN", userId), "订单信息查询失败"));
        result.put("payments", requireData(paymentClient.userSummary("ADMIN", userId), "支付信息查询失败"));
        return result;
    }

    public Map<String, Object> audits(String action, String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normalizedAction = normalizeFilter(action);
        String normalizedKeyword = normalizeFilter(keyword);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", userMapper.findAudits(normalizedAction, normalizedKeyword,
                (safePage - 1) * safeSize, safeSize));
        result.put("total", userMapper.countAudits(normalizedAction, normalizedKeyword));
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private Map<String, Object> issueToken(User user) {
        String token = JwtSupport.create(jwtSecret,
                new JwtPayload(user.getId(), user.getUsername(), user.getRole()), TOKEN_LIFETIME);
        redisTemplate.opsForValue().set("login:token:" + token, user.getId().toString(), TOKEN_LIFETIME);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("expiresIn", TOKEN_LIFETIME.toSeconds());
        result.put("user", publicUser(user));
        return result;
    }

    private Map<String, Object> publicUser(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("phone", user.getPhone());
        result.put("nickname", user.getNickname());
        result.put("role", user.getRole());
        return result;
    }

    private Map<String, Object> adminUser(User user) {
        Map<String, Object> result = publicUser(user);
        result.put("status", user.getStatus());
        result.put("createdAt", user.getCreatedAt());
        return result;
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> T requireData(ApiResponse<T> response, String message) {
        if (response == null || response.code() != 200) {
            throw new BusinessException(502, response == null ? message : response.message());
        }
        return response.data();
    }

    private void invalidateSessions(Long userId) {
        var keys = redisTemplate.keys("login:token:*");
        if (keys == null || keys.isEmpty()) return;
        for (String key : keys) {
            if (userId.toString().equals(redisTemplate.opsForValue().get(key))) redisTemplate.delete(key);
        }
    }
}
