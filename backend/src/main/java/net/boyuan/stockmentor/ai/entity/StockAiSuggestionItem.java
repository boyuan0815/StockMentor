package net.boyuan.stockmentor.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionItemStatus;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.auth.entity.AppUser;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "stock_ai_suggestion_item",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_suggestion_item_batch_symbol", columnNames = {"suggestion_batch_id", "symbol"})
        },
        indexes = {
                @Index(name = "idx_suggestion_item_user_status", columnList = "user_id, status"),
                @Index(name = "idx_suggestion_item_batch_rank", columnList = "suggestion_batch_id, rank_no")
        }
)
public class StockAiSuggestionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long suggestionItemId;

    @ManyToOne
    @JoinColumn(name = "suggestion_batch_id", nullable = false)
    private StockAiSuggestionBatch suggestionBatch;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "symbol", length = 10, nullable = false)
    private String symbol;

    @Column(name = "rank_no", nullable = false)
    private Integer rankNo;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "risk_level", length = 30)
    private String riskLevel;

    @Column(name = "suggestion_label", length = 100)
    private String suggestionLabel;

    @Column(name = "short_reason", length = 500)
    private String shortReason;

    @Column(name = "detail_reason", length = 2000)
    private String detailReason;

    @Lob
    @Column(name = "short_reason_highlights", columnDefinition = "TEXT")
    private String shortReasonHighlights;

    @Lob
    @Column(name = "detail_reason_highlights", columnDefinition = "TEXT")
    private String detailReasonHighlights;

    @ManyToOne
    @JoinColumn(name = "analysis_snapshot_id", nullable = false)
    private StockAnalysisSnapshot analysisSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StockAiSuggestionItemStatus status;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
