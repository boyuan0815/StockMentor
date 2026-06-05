package net.boyuan.stockmentor.userbehavior.service.impl;

import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.userbehavior.dto.BehaviorSummaryForSuggestion;
import net.boyuan.stockmentor.userbehavior.entity.UserBehaviorProfile;
import net.boyuan.stockmentor.userbehavior.model.UserBehaviorStyle;
import net.boyuan.stockmentor.userbehavior.repository.UserBehaviorProfileRepository;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBehaviorProfileServiceImplTests {
    @Mock
    private UserBehaviorProfileRepository behaviorProfileRepository;
    @Mock
    private AppUserRepository appUserRepository;

    private UserBehaviorProfileServiceImpl service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new UserBehaviorProfileServiceImpl(behaviorProfileRepository, appUserRepository);
        user = new AppUser();
        user.setUserId(1L);
        user.setEmail("beginner@example.com");
        user.setUsername("beginner");
        user.setRole(AppUserRole.BEGINNER_INVESTOR);
        user.setStatus(AppUserStatus.ACTIVE);
        user.setIsDeleted(false);
    }

    @Test
    void createsLowConfidenceProfileWhenMissing() {
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> {
            UserBehaviorProfile profile = invocation.getArgument(0);
            profile.setBehaviorProfileId(10L);
            return profile;
        });

        UserBehaviorProfile profile = service.createLowConfidenceProfileIfMissing(user);

        assertEquals(BehaviorConfidence.LOW, profile.getBehaviorConfidence());
        assertEquals(UserBehaviorStyle.INSUFFICIENT_DATA, profile.getBehaviorStyle());
        assertNotNull(profile.getCreatedAt());
        verify(behaviorProfileRepository).save(any(UserBehaviorProfile.class));
    }

    @Test
    void reusesExistingBehaviorProfileInsteadOfCreatingDuplicate() {
        UserBehaviorProfile existing = new UserBehaviorProfile();
        existing.setBehaviorProfileId(10L);
        existing.setUser(user);
        existing.setBehaviorConfidence(BehaviorConfidence.LOW);
        existing.setBehaviorStyle(UserBehaviorStyle.INSUFFICIENT_DATA);
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.of(existing));

        UserBehaviorProfile profile = service.createLowConfidenceProfileIfMissing(user);

        assertSame(existing, profile);
        verify(behaviorProfileRepository, never()).save(any(UserBehaviorProfile.class));
    }

    @Test
    void summaryIsLowConfidenceWhenNoProfileExistsAndDoesNotCreateProfile() {
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());

        BehaviorSummaryForSuggestion summary = service.getBehaviorSummaryForSuggestion(1L);

        assertFalse(summary.hasProfile());
        assertEquals(BehaviorConfidence.LOW, summary.behaviorConfidence());
        assertEquals(UserBehaviorStyle.INSUFFICIENT_DATA, summary.behaviorStyle());
        verify(behaviorProfileRepository, never()).save(any(UserBehaviorProfile.class));
    }
}
