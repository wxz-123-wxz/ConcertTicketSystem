package com.liveseat.order.client;

import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.dto.PaymentRefundCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "ticket-payment-service")
public interface PaymentClient {
    @PostMapping("/payments/internal/refund")
    ApiResponse<Map<String, Object>> refund(@RequestBody PaymentRefundCommand command);

    @GetMapping("/payments/internal/orders/{orderNo}/settlement")
    ApiResponse<Map<String, Object>> settlement(@PathVariable String orderNo);
}
