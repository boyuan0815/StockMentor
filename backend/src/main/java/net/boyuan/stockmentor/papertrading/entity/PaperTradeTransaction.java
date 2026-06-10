package net.boyuan.stockmentor.papertrading.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.papertrading.model.PaperTradeSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "paper_trade_transaction",
        indexes = {
                @Index(name = "idx_paper_trade_user_executed", columnList = "user_id, executed_at"),
                @Index(name = "idx_paper_trade_user_symbol", columnList = "user_id, symbol"),
                @Index(name = "idx_paper_trade_user_side", columnList = "user_id, side")
        }
)
public class PaperTradeTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "symbol", length = 10)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 10, nullable = false)
    private PaperTradeSide side;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "execution_price", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal executionPrice;

    @Column(name = "gross_amount", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal grossAmount;

    @Column(name = "fee", precision = 19, scale = 4, updatable = false)
    private BigDecimal fee;

    @Column(name = "net_amount", precision = 19, scale = 4, updatable = false)
    private BigDecimal netAmount;

    @Column(name = "realized_profit_loss", precision = 19, scale = 4, updatable = false)
    private BigDecimal realizedProfitLoss;

    @Column(name = "cash_balance_after", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal cashBalanceAfter;

    @Column(name = "is_current_session")
    private Boolean isCurrentSession;

    @Column(name = "session_number")
    private Integer sessionNumber;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private LocalDateTime executedAt;
}
