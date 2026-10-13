package com.liveseat.auth.client;

import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.security.UserHeaders;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "ticket-payment-service")
public interface PaymentClient {
    @GetMapping("/payments/admin/users/{userId}/summary")
    ApiResponse<Map<String, Object>> userSummary(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                  @PathVariable Long userId);
}
