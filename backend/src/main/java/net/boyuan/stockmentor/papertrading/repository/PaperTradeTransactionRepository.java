package net.boyuan.stockmentor.papertrading.repository;

import net.boyuan.stockmentor.papertrading.entity.PaperTradeTransaction;
import net.boyuan.stockmentor.papertrading.model.PaperTradeSide;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaperTradeTransactionRepository extends JpaRepository<PaperTradeTransaction, Long>,
        JpaSpecificationExecutor<PaperTradeTransaction> {
    List<PaperTradeTransaction> findTop50ByUserUserIdOrderByExecutedAtDesc(Long userId);

    Optional<PaperTradeTransaction> findByTransactionIdAndUserUserId(Long transactionId, Long userId);

    long countByUserUserId(Long userId);

    List<PaperTradeTransaction> findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );

    @Modifying
    @Query("""
            update PaperTradeTransaction transaction
            set transaction.isCurrentSession = false
            where transaction.user.userId = :userId
              and (transaction.isCurrentSession = true or transaction.isCurrentSession is null)
            """)
    int markCurrentSessionFalseByUserId(@Param("userId") Long userId);

    @Query("""
            select coalesce(sum(transaction.realizedProfitLoss), 0)
            from PaperTradeTransaction transaction
            where transaction.user.userId = :userId
              and transaction.side = :side
              and (transaction.isCurrentSession = true or transaction.isCurrentSession is null)
            """)
    BigDecimal sumCurrentSessionRealizedProfitLossByUserIdAndSide(
            @Param("userId") Long userId,
            @Param("side") PaperTradeSide side
    );

    @Query("""
            select coalesce(sum(transaction.fee), 0)
            from PaperTradeTransaction transaction
            where transaction.user.userId = :userId
              and transaction.side in :sides
              and (transaction.isCurrentSession = true or transaction.isCurrentSession is null)
            """)
    BigDecimal sumCurrentSessionFeeByUserIdAndSideIn(
            @Param("userId") Long userId,
            @Param("sides") List<PaperTradeSide> sides
    );
}
