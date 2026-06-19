package net.boyuan.stockmentor.auth.service;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.auth.dto.AuthUserResponse;
import net.boyuan.stockmentor.auth.dto.RegisterRequest;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.exception.RegistrationConflictException;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Pattern BASIC_EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 30;

    private final AppUserRepository appUserRepository;
    private final UserInvestmentProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    @Transactional
    public AuthUserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String username = normalizeUsername(request.username());
        validateRegistration(request, email, username);

        boolean emailExists = appUserRepository.existsByEmailIgnoreCase(email);
        boolean usernameExists = appUserRepository.existsByUsernameIgnoreCase(username);
        if (emailExists || usernameExists) {
            throw new RegistrationConflictException(emailExists, usernameExists);
        }

        LocalDateTime now = LocalDateTime.now();
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(AppUserRole.BEGINNER_INVESTOR);
        user.setStatus(AppUserStatus.ACTIVE);
        user.setIsDeleted(false);
        user.setOnboardingCompleted(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            return toResponse(appUserRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email or username is already registered", e);
        }
    }

    @Transactional
    public AuthUserResponse loginCurrentUser() {
        AppUser user = currentUserService.getCurrentUser();
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(user.getLastLoginAt());
        return toResponse(appUserRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthUserResponse getCurrentUser() {
        return toResponse(currentUserService.getCurrentUser());
    }

    private void validateRegistration(RegisterRequest request, String email, String username) {
        if (!BASIC_EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be valid");
        }
        if (username.length() < MIN_USERNAME_LENGTH || username.length() > MAX_USERNAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be 3 to 30 characters");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username may only contain letters, numbers, dots, underscores, and hyphens"
            );
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password and confirm password must match");
        }
    }

    private AuthUserResponse toResponse(AppUser user) {
        boolean hasInvestmentProfile = profileRepository
                .findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(user.getUserId())
                .isPresent();
        boolean onboardingCompleted = Boolean.TRUE.equals(user.getOnboardingCompleted());
        boolean mustCompleteOnboarding = user.getRole() == AppUserRole.BEGINNER_INVESTOR
                && (!onboardingCompleted || !hasInvestmentProfile);
        return new AuthUserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getOnboardingCompleted(),
                hasInvestmentProfile,
                mustCompleteOnboarding,
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }
}
