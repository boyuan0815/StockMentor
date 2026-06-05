package net.boyuan.stockmentor.papertrading.repository;

import net.boyuan.stockmentor.papertrading.entity.PaperTradeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PaperTradeTransactionRepository extends JpaRepository<PaperTradeTransaction, Long> {
    List<PaperTradeTransaction> findTop50ByUserUserIdOrderByExecutedAtDesc(Long userId);

    List<PaperTradeTransaction> findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );
}
