package com.liveseat.auth.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String phone;
    private String passwordHash;
    private String nickname;
    private String role;
    private String status;
    private LocalDateTime createdAt;
}

