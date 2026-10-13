package com.liveseat.common.dto;

import java.math.BigDecimal;

public record SeatInfo(Long id, String seatCode, String areaName, Integer rowNo,
                       Integer colNo, BigDecimal price, String status) {
}

