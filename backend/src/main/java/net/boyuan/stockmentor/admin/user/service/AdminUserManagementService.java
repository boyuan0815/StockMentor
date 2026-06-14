package net.boyuan.stockmentor.admin.user.service;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.admin.user.dto.AdminInvestmentProfileSummaryResponse;
import net.boyuan.stockmentor.admin.user.dto.AdminPaperTradingSummaryResponse;
import net.boyuan.stockmentor.admin.user.dto.AdminUpdateUserStatusRequest;
import net.boyuan.stockmentor.admin.user.dto.AdminUserDetailResponse;
import net.boyuan.stockmentor.admin.user.dto.AdminUserListItemResponse;
import net.boyuan.stockmentor.ai.dto.admin.AdminPageResponse;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.common.exception.ResourceNotFoundException;
import net.boyuan.stockmentor.papertrading.entity.PaperTradingAccount;
import net.boyuan.stockmentor.papertrading.model.PaperTradeSide;
import net.boyuan.stockmentor.papertrading.repository.PaperPositionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradeTransactionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradingAccountRepository;
import net.boyuan.stockmentor.userbehavior.entity.UserBehaviorProfile;
import net.boyuan.stockmentor.userbehavior.repository.UserBehaviorProfileRepository;
import net.boyuan.stockmentor.userprofile.dto.BehaviorProfileSummaryResponse;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminUserManagementService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final UserInvestmentProfileRepository profileRepository;
    private final UserBehaviorProfileRepository behaviorProfileRepository;
    private final PaperTradingAccountRepository paperTradingAccountRepository;
    private final PaperPositionRepository paperPositionRepository;
    private final PaperTradeTransactionRepository paperTradeTransactionRepository;

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminUserListItemResponse> listUsers(
            String search,
            String email,
            String username,
            String role,
            String status,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must not be negative");
        }
        if (size <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be greater than 0");
        }

        int pageSize = Math.min(size, MAX_PAGE_SIZE);
        AppUserRole parsedRole = parseRole(role);
        AppUserStatus parsedStatus = parseStatusFilter(status);
        PageRequest pageRequest = PageRequest.of(
                page,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<AppUser> users = appUserRepository.findAll(
                buildUserSpecification(search, email, username, parsedRole, parsedStatus),
                pageRequest
        );
        List<AdminUserListItemResponse> content = users.stream()
                .map(this::toListItem)
                .toList();

        return new AdminPageResponse<>(
                content,
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(Long userId) {
        AppUser user = findUser(userId);
        return toDetail(user);
    }

    @Transactional
    public AdminUserDetailResponse updateUserStatus(Long userId, AdminUpdateUserStatusRequest request) {
        AppUser target = findUser(userId);
        AppUserStatus requestedStatus = parseAllowedUpdateStatus(request.status());

        if (Boolean.TRUE.equals(target.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deleted users cannot be enabled or disabled");
        }
        if (target.getStatus() == requestedStatus) {
            return toDetail(target);
        }
        if (target.getRole() == AppUserRole.ADMIN && requestedStatus == AppUserStatus.INACTIVE) {
            AppUser currentAdmin = currentUserService.getCurrentUser();
            if (target.getUserId().equals(currentAdmin.getUserId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin cannot disable own account");
            }
            long activeAdminCount = appUserRepository.countByRoleAndStatusAndIsDeletedFalse(
                    AppUserRole.ADMIN,
                    AppUserStatus.ACTIVE
            );
            if (target.getStatus() == AppUserStatus.ACTIVE && activeAdminCount <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot disable the last active admin");
            }
        }

        target.setStatus(requestedStatus);
        target.setUpdatedAt(LocalDateTime.now());
        return toDetail(appUserRepository.save(target));
    }

    private AppUser findUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Specification<AppUser> buildUserSpecification(
            String search,
            String email,
            String username,
            AppUserRole role,
            AppUserStatus status
    ) {
        Specification<AppUser> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.isFalse(root.get("isDeleted"));

        String normalizedSearch = normalizeFilter(search);
        if (!normalizedSearch.isBlank()) {
            specification = specification.and((root, query, criteriaBuilder) -> {
                String likeValue = "%" + normalizedSearch + "%";
                return criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), likeValue),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), likeValue)
                );
            });
        }

        String normalizedEmail = normalizeFilter(email);
        if (!normalizedEmail.isBlank()) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), "%" + normalizedEmail + "%"));
        }

        String normalizedUsername = normalizeFilter(username);
        if (!normalizedUsername.isBlank()) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), "%" + normalizedUsername + "%"));
        }

        if (role != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("role"), role));
        }

        if (status != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));
        }

        return specification;
    }

    private AdminUserDetailResponse toDetail(AppUser user) {
        return new AdminUserDetailResponse(
                toListItem(user),
                profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(user.getUserId())
                        .map(this::toProfileSummary)
                        .orElse(null),
                behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(user.getUserId())
                        .map(this::toBehaviorSummary)
                        .orElse(null),
                paperTradingAccountRepository.findByUserUserId(user.getUserId())
                        .map(this::toPaperTradingSummary)
                        .orElse(null)
        );
    }

    private AdminUserListItemResponse toListItem(AppUser user) {
        return new AdminUserListItemResponse(
                user.getUserId(),
                user.getEmail(),
                user.getUsername(),
                enumName(user.getRole()),
                enumName(user.getStatus()),
                user.getIsDeleted(),
                user.getOnboardingCompleted(),
                profileRepository.existsByUserUserId(user.getUserId()),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt()
        );
    }

    private AdminInvestmentProfileSummaryResponse toProfileSummary(UserInvestmentProfile profile) {
        return new AdminInvestmentProfileSummaryResponse(
                profile.getProfileId(),
                profile.getProfileVersion(),
                enumName(profile.getProfileSource()),
                enumName(profile.getRiskTolerance()),
                enumName(profile.getInvestmentGoal()),
                enumName(profile.getExperienceLevel()),
                enumName(profile.getPreferredVolatility()),
                enumName(profile.getPreferredHorizon()),
                profile.getRiskScore(),
                profile.getGoalScore(),
                profile.getExperienceScore(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    private BehaviorProfileSummaryResponse toBehaviorSummary(UserBehaviorProfile profile) {
        return new BehaviorProfileSummaryResponse(
                profile.getBehaviorProfileId(),
                enumName(profile.getBehaviorConfidence()),
                enumName(profile.getBehaviorStyle()),
                profile.getBehaviorRiskScore(),
                profile.getBehaviorSummaryText(),
                "Latest stored behavior profile",
                profile.getUpdatedAt()
        );
    }

    private AdminPaperTradingSummaryResponse toPaperTradingSummary(PaperTradingAccount account) {
        Long userId = account.getUser().getUserId();
        BigDecimal realizedProfitLoss = paperTradeTransactionRepository
                .sumCurrentSessionRealizedProfitLossByUserIdAndSide(userId, PaperTradeSide.SELL);
        return new AdminPaperTradingSummaryResponse(
                enumName(account.getStatus()),
                account.getCashBalance(),
                realizedProfitLoss == null ? BigDecimal.ZERO : realizedProfitLoss,
                account.getCurrentSessionNumber(),
                paperPositionRepository.countByUserUserId(userId),
                paperTradeTransactionRepository.countByUserUserId(userId),
                account.getLastResetAt(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private AppUserRole parseRole(String value) {
        String normalized = normalizeEnumInput(value);
        if (normalized == null) {
            return null;
        }
        try {
            return AppUserRole.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user role: " + value);
        }
    }

    private AppUserStatus parseStatusFilter(String value) {
        String normalized = normalizeEnumInput(value);
        if (normalized == null) {
            return null;
        }
        try {
            return AppUserStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user status: " + value);
        }
    }

    private AppUserStatus parseAllowedUpdateStatus(String value) {
        AppUserStatus status = parseStatusFilter(value);
        if (status != AppUserStatus.ACTIVE && status != AppUserStatus.INACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or INACTIVE");
        }
        return status;
    }

    private static String normalizeEnumInput(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeFilter(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
