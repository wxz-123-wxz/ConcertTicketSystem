package com.liveseat.common.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SeatLockCommand(@NotNull Long userId, @NotNull Long sessionId,
                              @NotEmpty List<Long> seatIds) {
}

