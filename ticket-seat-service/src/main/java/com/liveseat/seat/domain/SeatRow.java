package com.liveseat.seat.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeatRow {
    private Long id;
    private Long sessionId;
    private Long categoryId;
    private String seatCode;
    private String areaName;
    private Integer rowNo;
    private Integer colNo;
    private BigDecimal price;
    private String status;
    private String lockToken;
    private LocalDateTime lockExpireTime;
}
