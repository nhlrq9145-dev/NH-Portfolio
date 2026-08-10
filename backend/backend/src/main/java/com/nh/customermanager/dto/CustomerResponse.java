package com.nh.customermanager.dto;

import java.time.LocalDateTime;

public record CustomerResponse(
        Long id,
        String name,
        String phone,
        String email,
        String status,
        LocalDateTime createdAt
) {
}
