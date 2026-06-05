package net.boyuan.stockmentor.config;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import net.boyuan.stockmentor.userprofile.model.*;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Configuration
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder {
    private static final String DEMO_EMAIL = "demo@stockmentor.local";
    private static final String DEMO_USERNAME = "demo";
    private static final String ADMIN_EMAIL = "admin@stockmentor.local";
    private static final String ADMIN_USERNAME = "admin";

    private final AppUserRepository appUserRepository;
    private final UserInvestmentProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner seedDevUser() {
        return args -> {
            LocalDateTime now = LocalDateTime.now();
            AppUser user = appUserRepository.findByEmailOrUsername(DEMO_EMAIL, DEMO_USERNAME)
                    .orElseGet(() -> {
                        AppUser newUser = new AppUser();
                        newUser.setEmail(DEMO_EMAIL);
                        newUser.setUsername(DEMO_USERNAME);
                        newUser.setPasswordHash(passwordEncoder.encode("Demo@12345"));
                        newUser.setRole(AppUserRole.BEGINNER_INVESTOR);
                        newUser.setStatus(AppUserStatus.ACTIVE);
                        newUser.setIsDeleted(false);
                        newUser.setOnboardingCompleted(true);
                        newUser.setCreatedAt(now);
                        newUser.setUpdatedAt(now);
                        return appUserRepository.save(newUser);
                    });

            profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(user.getUserId())
                    .orElseGet(() -> {
                        UserInvestmentProfile profile = new UserInvestmentProfile();
                        profile.setUser(user);
                        profile.setRiskTolerance(RiskTolerance.MODERATE);
                        profile.setInvestmentGoal(InvestmentGoal.GROWTH);
                        profile.setExperienceLevel(ExperienceLevel.BEGINNER);
                        profile.setPreferredVolatility(PreferredVolatility.MEDIUM);
                        profile.setPreferredHorizon(PreferredHorizon.MEDIUM_TERM);
                        profile.setRiskScore(55);
                        profile.setGoalScore(70);
                        profile.setExperienceScore(20);
                        profile.setBehaviorRiskScore(null);
                        profile.setBehaviorStyle(null);
                        profile.setBehaviorConfidence(BehaviorConfidence.LOW);
                        profile.setProfileSource(ProfileSource.ONBOARDING);
                        profile.setProfileVersion(1);
                        profile.setCreatedAt(now);
                        profile.setUpdatedAt(now);
                        return profileRepository.save(profile);
                    });

            appUserRepository.findByEmailOrUsername(ADMIN_EMAIL, ADMIN_USERNAME)
                    .orElseGet(() -> {
                        AppUser adminUser = new AppUser();
                        adminUser.setEmail(ADMIN_EMAIL);
                        adminUser.setUsername(ADMIN_USERNAME);
                        adminUser.setPasswordHash(passwordEncoder.encode("Admin@12345"));
                        adminUser.setRole(AppUserRole.ADMIN);
                        adminUser.setStatus(AppUserStatus.ACTIVE);
                        adminUser.setIsDeleted(false);
                        adminUser.setOnboardingCompleted(false);
                        adminUser.setCreatedAt(now);
                        adminUser.setUpdatedAt(now);
                        return appUserRepository.save(adminUser);
                    });
        };
    }
}
