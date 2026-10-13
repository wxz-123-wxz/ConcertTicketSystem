package com.liveseat.order.controller;

import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.dto.OrderPaymentCommand;
import com.liveseat.common.dto.OrderSummary;
import com.liveseat.common.security.UserHeaders;
import com.liveseat.common.exception.BusinessException;
import com.liveseat.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                   @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok("订单创建成功", service.create(userId, request.sessionId(), request.seatIds()));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestHeader(UserHeaders.USER_ID) Long userId) {
        return ApiResponse.ok(service.list(userId));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Map<String, Object>> detail(@RequestHeader(UserHeaders.USER_ID) Long userId, @PathVariable String orderNo) {
        return ApiResponse.ok(service.detail(userId, orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<Void> cancel(@RequestHeader(UserHeaders.USER_ID) Long userId, @PathVariable String orderNo) {
        service.cancel(userId, orderNo);
        return ApiResponse.ok("订单已取消", null);
    }

    @PostMapping("/{orderNo}/refunds")
    public ApiResponse<Map<String, Object>> requestRefund(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                          @PathVariable String orderNo,
                                                          @Valid @RequestBody RefundRequest request) {
        return ApiResponse.ok("退款申请已提交", service.requestRefund(userId, orderNo, request.reason()));
    }

    @GetMapping("/{orderNo}/tickets")
    public ApiResponse<List<Map<String, Object>>> tickets(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                          @PathVariable String orderNo) {
        return ApiResponse.ok(service.tickets(userId, orderNo));
    }

    @GetMapping("/admin/list")
    public ApiResponse<Map<String, Object>> adminList(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return ApiResponse.ok(service.adminList(status, keyword, page, size));
    }

    @GetMapping("/admin/{orderNo}")
    public ApiResponse<Map<String, Object>> adminDetail(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                        @PathVariable String orderNo) {
        requireAdmin(role);
        return ApiResponse.ok(service.adminDetail(orderNo));
    }

    @GetMapping("/admin/refunds")
    public ApiResponse<Map<String, Object>> refunds(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return ApiResponse.ok(service.refunds(status, page, size));
    }

    @PostMapping("/admin/refunds/{refundNo}/review")
    public ApiResponse<Map<String, Object>> reviewRefund(@RequestHeader(UserHeaders.USER_ID) Long operatorId,
                                                         @RequestHeader(UserHeaders.USER_NAME) String operatorName,
                                                         @RequestHeader(UserHeaders.USER_ROLE) String role,
                                                         @PathVariable String refundNo,
                                                         @Valid @RequestBody RefundReview request) {
        requireAdmin(role);
        return ApiResponse.ok("退款审核已完成",
                service.reviewRefund(operatorId, operatorName, refundNo, request.action(), request.note()));
    }

    @GetMapping("/admin/tickets/{ticketCode}")
    public ApiResponse<Map<String, Object>> ticket(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                   @PathVariable String ticketCode) {
        requireAdmin(role);
        return ApiResponse.ok(service.ticket(ticketCode));
    }

    @PostMapping("/admin/tickets/{ticketCode}/check-in")
    public ApiResponse<Map<String, Object>> checkIn(@RequestHeader(UserHeaders.USER_ID) Long operatorId,
                                                    @RequestHeader(UserHeaders.USER_NAME) String operatorName,
                                                    @RequestHeader(UserHeaders.USER_ROLE) String role,
                                                    @PathVariable String ticketCode) {
        requireAdmin(role);
        return ApiResponse.ok("电子票核销成功", service.checkIn(operatorId, operatorName, ticketCode));
    }

    @GetMapping("/admin/metrics")
    public ApiResponse<Map<String, Object>> metrics(@RequestHeader(UserHeaders.USER_ROLE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.metrics());
    }

    @GetMapping("/admin/users/{userId}/summary")
    public ApiResponse<Map<String, Object>> userSummary(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                        @PathVariable Long userId) {
        requireAdmin(role);
        return ApiResponse.ok(service.userSummary(userId));
    }

    @GetMapping("/internal/{orderNo}")
    public ApiResponse<OrderSummary> summary(@RequestParam Long userId, @PathVariable String orderNo) {
        return ApiResponse.ok(service.summary(userId, orderNo));
    }

    @PostMapping("/internal/confirm-payment")
    public ApiResponse<OrderSummary> confirmPayment(@Valid @RequestBody OrderPaymentCommand command) {
        return ApiResponse.ok(service.confirmPayment(command));
    }

    public record CreateOrderRequest(@NotNull Long sessionId, @NotEmpty List<Long> seatIds) {}
    public record RefundRequest(@NotBlank @Size(max = 300) String reason) {}
    public record RefundReview(@NotBlank String action, @Size(max = 300) String note) {}

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) throw new BusinessException(403, "仅管理员可执行此操作");
    }
}
