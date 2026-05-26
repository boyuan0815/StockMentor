package net.boyuan.stockmentor.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "stock_ai_explanation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ai_snapshot_model_prompt",
                        columnNames = {"analysis_snapshot_id", "model", "prompt_version"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_ai_symbol_timeframe_created",
                        columnList = "symbol, timeframe, created_at"
                )
        }
)
public class StockAiExplanation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long explanationId;

    @ManyToOne
    @JoinColumn(name = "analysis_snapshot_id", nullable = false)
    private StockAnalysisSnapshot analysisSnapshot;

    @Column(name = "symbol", length = 10, nullable = false)
    private String symbol;

    @Column(name = "timeframe", length = 10, nullable = false)
    private String timeframe;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "explanation", length = 2000)
    private String explanation;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "finish_reason")
    private String finishReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
