package com.liveseat.payment.client;

import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.dto.OrderPaymentCommand;
import com.liveseat.common.dto.OrderSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "ticket-order-service")
public interface OrderClient {
    @GetMapping("/orders/internal/{orderNo}")
    ApiResponse<OrderSummary> summary(@PathVariable String orderNo, @RequestParam Long userId);

    @PostMapping("/orders/internal/confirm-payment")
    ApiResponse<OrderSummary> confirm(@RequestBody OrderPaymentCommand command);
}
