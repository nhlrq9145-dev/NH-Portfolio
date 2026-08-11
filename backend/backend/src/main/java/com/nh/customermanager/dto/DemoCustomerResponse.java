package com.nh.customermanager.dto;

public record DemoCustomerResponse(
        String displayName,
        String industry,
        String status
) {
}
