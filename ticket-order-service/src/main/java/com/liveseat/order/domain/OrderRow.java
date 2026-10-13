package com.liveseat.order.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderRow {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long sessionId;
    private String holdToken;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
}
