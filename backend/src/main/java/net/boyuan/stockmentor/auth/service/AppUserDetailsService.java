package net.boyuan.stockmentor.auth.service;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository appUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = appUserRepository.findByEmailOrUsername(username, username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (appUser.getStatus() != AppUserStatus.ACTIVE || Boolean.TRUE.equals(appUser.getIsDeleted())) {
            throw new DisabledException("User account is not active");
        }

        return User.withUsername(appUser.getEmail())
                .password(appUser.getPasswordHash())
                .authorities("ROLE_" + appUser.getRole().name())
                .build();
    }
}
