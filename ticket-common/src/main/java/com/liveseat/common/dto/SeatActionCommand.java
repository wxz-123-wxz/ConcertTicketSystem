package com.liveseat.common.dto;

import java.util.List;

public record SeatActionCommand(Long userId, Long sessionId, List<Long> seatIds, String holdToken) {
}

