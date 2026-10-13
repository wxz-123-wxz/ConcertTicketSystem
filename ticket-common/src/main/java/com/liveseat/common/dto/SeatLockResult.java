package com.liveseat.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SeatLockResult(String holdToken, LocalDateTime expireTime,
                             BigDecimal totalAmount, List<SeatInfo> seats) {
}

