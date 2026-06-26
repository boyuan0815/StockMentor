package net.boyuan.stockmentor.watchlist.repository;

import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserWatchlistRepository extends JpaRepository<UserWatchlist, Long> {
    Optional<UserWatchlist> findByUserUserIdAndSymbol(Long userId, String symbol);

    boolean existsByUserUserIdAndSymbol(Long userId, String symbol);

    List<UserWatchlist> findByUserUserId(Long userId);

    List<UserWatchlist> findByUserUserIdOrderByDisplayOrderAscCreatedAtAscWatchlistIdAsc(Long userId);

    List<UserWatchlist> findByUserUserIdAndSymbolIn(Long userId, Collection<String> symbols);
}
