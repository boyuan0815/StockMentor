package net.boyuan.stockmentor.admin.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.admin.AdminTokenValidator;
import net.boyuan.stockmentor.admin.user.dto.AdminUpdateUserStatusRequest;
import net.boyuan.stockmentor.admin.user.dto.AdminUserDetailResponse;
import net.boyuan.stockmentor.admin.user.dto.AdminUserListItemResponse;
import net.boyuan.stockmentor.admin.user.service.AdminUserManagementService;
import net.boyuan.stockmentor.ai.dto.admin.AdminPageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminTokenValidator adminTokenValidator;
    private final AdminUserManagementService adminUserManagementService;

    @GetMapping
    public AdminPageResponse<AdminUserListItemResponse> listUsers(
            @RequestHeader(name = "X-Admin-Token", required = false) String adminToken,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        adminTokenValidator.validate(adminToken);
        return adminUserManagementService.listUsers(search, email, username, role, status, page, size);
    }

    @GetMapping("/{userId}")
    public AdminUserDetailResponse getUserDetail(
            @RequestHeader(name = "X-Admin-Token", required = false) String adminToken,
            @PathVariable Long userId
    ) {
        adminTokenValidator.validate(adminToken);
        return adminUserManagementService.getUserDetail(userId);
    }

    @PatchMapping("/{userId}/status")
    public AdminUserDetailResponse updateUserStatus(
            @RequestHeader(name = "X-Admin-Token", required = false) String adminToken,
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUserStatusRequest request
    ) {
        adminTokenValidator.validate(adminToken);
        return adminUserManagementService.updateUserStatus(userId, request);
    }
}
