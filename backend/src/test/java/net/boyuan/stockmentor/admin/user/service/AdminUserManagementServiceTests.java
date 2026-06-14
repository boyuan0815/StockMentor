package net.boyuan.stockmentor.admin.user.service;

import net.boyuan.stockmentor.admin.user.dto.AdminUpdateUserStatusRequest;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.papertrading.repository.PaperPositionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradeTransactionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradingAccountRepository;
import net.boyuan.stockmentor.userbehavior.repository.UserBehaviorProfileRepository;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementServiceTests {
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private UserInvestmentProfileRepository profileRepository;
    @Mock
    private UserBehaviorProfileRepository behaviorProfileRepository;
    @Mock
    private PaperTradingAccountRepository paperTradingAccountRepository;
    @Mock
    private PaperPositionRepository paperPositionRepository;
    @Mock
    private PaperTradeTransactionRepository paperTradeTransactionRepository;

    private AdminUserManagementService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserManagementService(
                appUserRepository,
                currentUserService,
                profileRepository,
                behaviorProfileRepository,
                paperTradingAccountRepository,
                paperPositionRepository,
                paperTradeTransactionRepository
        );
    }

    @Test
    void updateUserStatusRejectsDisablingLastActiveNonDeletedAdmin() {
        AppUser admin = new AppUser();
        admin.setUserId(1L);
        admin.setRole(AppUserRole.ADMIN);
        admin.setStatus(AppUserStatus.ACTIVE);
        admin.setIsDeleted(false);
        AppUser currentAdmin = new AppUser();
        currentAdmin.setUserId(99L);

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(currentUserService.getCurrentUser()).thenReturn(currentAdmin);
        when(appUserRepository.countByRoleAndStatusAndIsDeletedFalse(AppUserRole.ADMIN, AppUserStatus.ACTIVE))
                .thenReturn(1L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateUserStatus(1L, new AdminUpdateUserStatusRequest("INACTIVE"))
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals("Cannot disable the last active admin", exception.getReason());
    }
}
