package com.liveseat.payment.controller;

import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.security.UserHeaders;
import com.liveseat.common.dto.PaymentRefundCommand;
import com.liveseat.common.exception.BusinessException;
import com.liveseat.payment.service.PaymentService;
import com.liveseat.payment.gateway.WechatMockPaymentService;
import com.liveseat.payment.gateway.LocalNetworkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService service;
    private final WechatMockPaymentService wechatMockService;
    private final LocalNetworkService localNetworkService;

    public PaymentController(PaymentService service, WechatMockPaymentService wechatMockService,
                             LocalNetworkService localNetworkService) {
        this.service = service;
        this.wechatMockService = wechatMockService;
        this.localNetworkService = localNetworkService;
    }

    @PostMapping
    public ApiResponse<Void> legacyPayment() {
        throw new BusinessException(410, "旧版微信即时确认接口已停用，请使用微信 Native 模拟支付");
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Map<String, Object>> query(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                  @PathVariable String orderNo) {
        return ApiResponse.ok(service.query(userId, orderNo));
    }

    @PostMapping("/alipay/page-pay")
    public ApiResponse<Map<String, Object>> alipayPagePay(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                          @Valid @RequestBody AlipayPayRequest request) {
        return ApiResponse.ok(service.createAlipayPagePay(userId, request.orderNo()));
    }

    @PostMapping("/alipay/query/{orderNo}")
    public ApiResponse<Map<String, Object>> alipayQuery(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                        @PathVariable String orderNo) {
        return ApiResponse.ok(service.syncAlipay(userId, orderNo));
    }

    @PostMapping("/wechat/native")
    public ApiResponse<Map<String, Object>> wechatNative(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                         @Valid @RequestBody WechatPayRequest request) {
        return ApiResponse.ok(wechatMockService.create(userId, request.orderNo()));
    }

    @GetMapping("/wechat/network-origin")
    public ApiResponse<Map<String, Object>> wechatNetworkOrigin() {
        return ApiResponse.ok(localNetworkService.frontendOrigin());
    }

    @GetMapping("/wechat/status/{orderNo}")
    public ApiResponse<Map<String, Object>> wechatStatus(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                         @PathVariable String orderNo) {
        return ApiResponse.ok(wechatMockService.status(userId, orderNo));
    }

    @GetMapping("/wechat/mock/{token}")
    public ApiResponse<Map<String, Object>> wechatMockDetail(@PathVariable String token) {
        return ApiResponse.ok(wechatMockService.detail(token));
    }

    @PostMapping("/wechat/mock/{token}/confirm")
    public ApiResponse<Map<String, Object>> wechatMockConfirm(@PathVariable String token) {
        return ApiResponse.ok("模拟支付确认成功", wechatMockService.confirm(token));
    }

    @GetMapping("/admin/list")
    public ApiResponse<Map<String, Object>> adminList(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String channel,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return ApiResponse.ok(service.adminList(status, channel, keyword, page, size));
    }

    @GetMapping("/admin/{paymentNo}")
    public ApiResponse<Map<String, Object>> adminDetail(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                        @PathVariable String paymentNo) {
        requireAdmin(role);
        return ApiResponse.ok(service.adminDetail(paymentNo));
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

    @PostMapping("/internal/refund")
    public ApiResponse<Map<String, Object>> refund(@RequestBody PaymentRefundCommand command) {
        return ApiResponse.ok("支付流水已退款", service.refund(command));
    }

    @GetMapping("/internal/orders/{orderNo}/settlement")
    public ApiResponse<Map<String, Object>> settlement(@PathVariable String orderNo) {
        return ApiResponse.ok(service.settlement(orderNo));
    }

    public record AlipayPayRequest(@NotBlank String orderNo) {}
    public record WechatPayRequest(@NotBlank String orderNo) {}

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) throw new BusinessException(403, "仅管理员可执行此操作");
    }
}
