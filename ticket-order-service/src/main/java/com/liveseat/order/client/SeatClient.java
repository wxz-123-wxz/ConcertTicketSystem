package com.liveseat.order.client;

import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.dto.SeatActionCommand;
import com.liveseat.common.dto.SeatLockCommand;
import com.liveseat.common.dto.SeatLockResult;
import com.liveseat.common.dto.RefundSeatCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "ticket-seat-service")
public interface SeatClient {
    @PostMapping("/seats/lock")
    ApiResponse<SeatLockResult> lock(@RequestBody SeatLockCommand command);

    @PostMapping("/seats/release")
    ApiResponse<Void> release(@RequestBody SeatActionCommand command);

    @PostMapping("/seats/confirm")
    ApiResponse<Void> confirm(@RequestParam String orderNo, @RequestBody SeatActionCommand command);

    @PostMapping("/seats/refund")
    ApiResponse<Void> refund(@RequestBody RefundSeatCommand command);
}
