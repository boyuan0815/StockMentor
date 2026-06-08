package net.boyuan.stockmentor.ai.dto.admin;

import java.util.List;

public record AdminPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
