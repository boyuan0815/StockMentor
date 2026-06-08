package net.boyuan.stockmentor.ai.service.impl;

import net.boyuan.stockmentor.ai.dto.StockAiSuggestionResponse;
import net.boyuan.stockmentor.ai.dto.SuggestionTriggerResult;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionService;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAiSuggestionTriggerServiceImplTests {
    @Mock
    private StockAiSuggestionService stockAiSuggestionService;

    private StockAiSuggestionTriggerServiceImpl service;
    private AppUser user;
    private UserInvestmentProfile profile;

    @BeforeEach
    void setUp() {
        service = new StockAiSuggestionTriggerServiceImpl(stockAiSuggestionService);
        user = new AppUser();
        user.setUserId(1L);
        user.setEmail("beginner@example.com");
        user.setUsername("beginner");
        user.setRole(AppUserRole.BEGINNER_INVESTOR);
        user.setStatus(AppUserStatus.ACTIVE);
        user.setIsDeleted(false);

        profile = new UserInvestmentProfile();
        profile.setProfileId(10L);
        profile.setUser(user);
        profile.setProfileVersion(2);
    }

    @Test
    void onboardingTriggerUsesOnboardingReasonAndBypassesManualCooldown() {
        when(stockAiSuggestionService.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.ONBOARDING_COMPLETED,
                false
        )).thenReturn(response("SUCCESS", "Generated new AI stock suggestions"));

        SuggestionTriggerResult result = service.handleOnboardingCompleted(user);

        assertTrue(result.attempted());
        assertTrue(result.successful());
        assertFalse(result.failed());
        assertEquals(5L, result.batchId());
        assertEquals("SUCCESS", result.batchStatus());
        assertEquals(StockAiSuggestionTriggerReason.ONBOARDING_COMPLETED, result.triggerReason());
        verify(stockAiSuggestionService).generateSuggestionsForUser(user, StockAiSuggestionTriggerReason.ONBOARDING_COMPLETED, false);
    }

    @Test
    void retakeTriggerUsesRetakeReasonAndBypassesManualCooldown() {
        when(stockAiSuggestionService.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.RETAKE_QUIZ,
                false
        )).thenReturn(response("SUCCESS", "Returned existing suggestions because your profile and stock data are unchanged."));

        SuggestionTriggerResult result = service.handleProfileRetaken(user, profile);

        assertTrue(result.successful());
        assertEquals(5L, result.batchId());
        assertEquals("SUCCESS", result.batchStatus());
        assertEquals(StockAiSuggestionTriggerReason.RETAKE_QUIZ, result.triggerReason());
        assertTrue(result.message().contains("unchanged"));
        verify(stockAiSuggestionService).generateSuggestionsForUser(user, StockAiSuggestionTriggerReason.RETAKE_QUIZ, false);
    }

    @Test
    void profileChangeTriggerAcceptsNoActiveSuggestionForExplicitBackendTrigger() {
        when(stockAiSuggestionService.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.NO_ACTIVE_SUGGESTION,
                false
        )).thenReturn(response("FALLBACK_RULE_BASED", "Suggestion trigger completed"));

        SuggestionTriggerResult result = service.handleInvestmentProfileChanged(
                user,
                null,
                StockAiSuggestionTriggerReason.NO_ACTIVE_SUGGESTION
        );

        assertTrue(result.successful());
        assertEquals("FALLBACK_RULE_BASED", result.batchStatus());
        assertEquals(StockAiSuggestionTriggerReason.NO_ACTIVE_SUGGESTION, result.triggerReason());
        verify(stockAiSuggestionService).generateSuggestionsForUser(user, StockAiSuggestionTriggerReason.NO_ACTIVE_SUGGESTION, false);
    }

    @Test
    void profileChangeTriggerRejectsManualAndScheduledReasons() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.handleInvestmentProfileChanged(user, profile, StockAiSuggestionTriggerReason.MANUAL_REFRESH)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.handleInvestmentProfileChanged(user, profile, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH)
        );

        verifyNoInteractions(stockAiSuggestionService);
    }

    @Test
    void profileChangeTriggerRequiresSavedProfileForProfileRelatedReasons() {
        UserInvestmentProfile unsaved = new UserInvestmentProfile();
        unsaved.setUser(user);
        unsaved.setProfileVersion(2);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.handleInvestmentProfileChanged(user, unsaved, StockAiSuggestionTriggerReason.RETAKE_QUIZ)
        );

        assertTrue(exception.getMessage().contains("Saved investment profile"));
        verifyNoInteractions(stockAiSuggestionService);
    }

    @Test
    void handleProfileRetakenRejectsSavedProfileBelongingToAnotherUser() {
        AppUser otherUser = new AppUser();
        otherUser.setUserId(2L);
        otherUser.setEmail("other@example.com");
        otherUser.setUsername("other");
        otherUser.setRole(AppUserRole.BEGINNER_INVESTOR);
        otherUser.setStatus(AppUserStatus.ACTIVE);
        otherUser.setIsDeleted(false);

        UserInvestmentProfile otherProfile = new UserInvestmentProfile();
        otherProfile.setProfileId(20L);
        otherProfile.setUser(otherUser);
        otherProfile.setProfileVersion(1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.handleProfileRetaken(user, otherProfile)
        );

        assertTrue(exception.getMessage().contains("triggering user"));
        verifyNoInteractions(stockAiSuggestionService);
    }

    @Test
    void generationFailureReturnsFailedTriggerResultWithoutThrowing() {
        when(stockAiSuggestionService.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.ONBOARDING_COMPLETED,
                false
        )).thenThrow(new IllegalStateException("OpenAI unavailable"));

        SuggestionTriggerResult result = service.handleOnboardingCompleted(user);

        assertTrue(result.attempted());
        assertFalse(result.successful());
        assertTrue(result.failed());
        assertNull(result.batchId());
        assertNull(result.batchStatus());
        assertEquals(StockAiSuggestionTriggerReason.ONBOARDING_COMPLETED, result.triggerReason());
        assertEquals("Suggestion trigger failed", result.message());
    }

    @Test
    void triggerResultDoesNotExposeOpenAiInternals() {
        when(stockAiSuggestionService.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.RETAKE_QUIZ,
                false
        )).thenReturn(response("SUCCESS", "Suggestion trigger completed"));

        SuggestionTriggerResult result = service.handleProfileRetaken(user, profile);

        String serializedShape = List.of(
                result.message(),
                String.valueOf(result.batchStatus()),
                String.valueOf(result.triggerReason())
        ).toString();
        assertFalse(serializedShape.contains("prompt"));
        assertFalse(serializedShape.contains("token"));
        assertFalse(serializedShape.contains("gpt-4o-mini"));
        assertFalse(serializedShape.contains("raw"));
    }

    private StockAiSuggestionResponse response(String batchStatus, String message) {
        return new StockAiSuggestionResponse(
                1L,
                5L,
                batchStatus,
                StockAiSuggestionTriggerReason.ONBOARDING_COMPLETED.name(),
                "summary",
                "7D",
                null,
                null,
                false,
                true,
                null,
                List.of(),
                List.of(),
                message
        );
    }
}
