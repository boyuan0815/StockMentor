package net.boyuan.stockmentor.watchlist.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.watchlist.model.WatchlistSource;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "user_watchlist",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_watchlist_user_symbol", columnNames = {"user_id", "symbol"})
        },
        indexes = {
                @Index(name = "idx_watchlist_user_symbol", columnList = "user_id, symbol")
        }
)
public class UserWatchlist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long watchlistId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "symbol", length = 10, nullable = false)
    private String symbol;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private WatchlistSource source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
