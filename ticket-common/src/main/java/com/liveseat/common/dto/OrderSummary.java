package com.liveseat.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderSummary(String orderNo, Long userId, Long sessionId, String status,
                           BigDecimal totalAmount, String holdToken, List<Long> seatIds,
                           LocalDateTime expireTime) {
}
