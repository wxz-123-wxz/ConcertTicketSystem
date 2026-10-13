package com.liveseat.common.security;

public record JwtPayload(Long userId, String username, String role) {
}

