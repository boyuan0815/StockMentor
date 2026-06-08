package net.boyuan.stockmentor.papertrading.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.boyuan.stockmentor.auth.entity.AppUser;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "paper_position",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_paper_position_user_symbol", columnNames = {"user_id", "symbol"})
        },
        indexes = {
                @Index(name = "idx_paper_position_user", columnList = "user_id")
        }
)
public class PaperPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long positionId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "symbol", length = 10, nullable = false)
    private String symbol;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "average_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal averageCost;

    @Column(name = "total_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalCost;

    @Column(name = "realized_pl", precision = 19, scale = 4, nullable = false)
    private BigDecimal realizedPl = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
