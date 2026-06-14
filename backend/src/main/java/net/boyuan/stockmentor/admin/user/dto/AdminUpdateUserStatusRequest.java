package net.boyuan.stockmentor.admin.user.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUpdateUserStatusRequest(
        @NotBlank(message = "Status is required")
        String status
) {
}
