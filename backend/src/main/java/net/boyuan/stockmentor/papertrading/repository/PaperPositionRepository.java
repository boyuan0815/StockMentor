package net.boyuan.stockmentor.papertrading.repository;

import net.boyuan.stockmentor.papertrading.entity.PaperPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperPositionRepository extends JpaRepository<PaperPosition, Long> {
    List<PaperPosition> findByUserUserId(Long userId);

    Optional<PaperPosition> findByUserUserIdAndSymbol(Long userId, String symbol);
}
