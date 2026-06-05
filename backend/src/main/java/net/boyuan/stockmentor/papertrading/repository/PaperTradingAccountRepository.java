package net.boyuan.stockmentor.papertrading.repository;

import net.boyuan.stockmentor.papertrading.entity.PaperTradingAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaperTradingAccountRepository extends JpaRepository<PaperTradingAccount, Long> {
    Optional<PaperTradingAccount> findByUserUserId(Long userId);
}
