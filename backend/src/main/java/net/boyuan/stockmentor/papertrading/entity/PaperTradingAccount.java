package net.boyuan.stockmentor.papertrading.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.papertrading.model.PaperTradingAccountStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "paper_trading_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_paper_trading_account_user", columnNames = "user_id")
        }
)
public class PaperTradingAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accountId;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "cash_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal cashBalance;

    @Column(name = "starting_cash", precision = 19, scale = 4, nullable = false)
    private BigDecimal startingCash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private PaperTradingAccountStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
