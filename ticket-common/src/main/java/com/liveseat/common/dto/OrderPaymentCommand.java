package com.liveseat.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderPaymentCommand(@NotNull Long userId, @NotBlank String orderNo, @NotBlank String channel) {
}
