package com.liveseat.common.dto;

import java.util.List;

public record RefundSeatCommand(Long userId, String orderNo, Long sessionId, List<Long> seatIds) {
}
