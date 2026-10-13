package com.liveseat.seat.controller;

import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.dto.*;
import com.liveseat.common.exception.BusinessException;
import com.liveseat.common.security.UserHeaders;
import com.liveseat.seat.service.SeatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/seats")
public class SeatController {
    private final SeatService service;

    public SeatController(SeatService service) { this.service = service; }

    @GetMapping("/session/{sessionId}")
    public ApiResponse<List<SeatInfo>> list(@PathVariable Long sessionId) { return ApiResponse.ok(service.list(sessionId)); }

    @GetMapping("/session/{sessionId}/recommend")
    public ApiResponse<List<SeatInfo>> recommend(@PathVariable Long sessionId,
                                                 @RequestParam(defaultValue = "2") int count,
                                                 @RequestParam(required = false) String areaName) {
        return ApiResponse.ok(service.recommend(sessionId, count, areaName));
    }

    @PostMapping("/lock")
    public ApiResponse<SeatLockResult> lock(@Valid @RequestBody SeatLockCommand command) {
        return ApiResponse.ok("座位已锁定，请在 10 分钟内支付", service.lock(command));
    }

    @PostMapping("/release")
    public ApiResponse<Void> release(@RequestBody SeatActionCommand command) {
        service.release(command);
        return ApiResponse.ok("座位已释放", null);
    }

    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@RequestParam String orderNo, @RequestBody SeatActionCommand command) {
        service.confirm(command, orderNo);
        return ApiResponse.ok("座位已确认售出", null);
    }

    @PostMapping("/refund")
    public ApiResponse<Void> refund(@RequestBody RefundSeatCommand command) {
        service.refund(command);
        return ApiResponse.ok("退款座位已释放", null);
    }

    @GetMapping("/admin/metrics")
    public ApiResponse<Map<String, Object>> metrics(@RequestHeader(UserHeaders.USER_ROLE) String role) {
        if (!"ADMIN".equals(role)) throw new BusinessException(403, "仅管理员可查看运营指标");
        return ApiResponse.ok(service.metrics());
    }
}
