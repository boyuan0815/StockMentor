package net.boyuan.stockmentor.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionBatchStatus;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "stock_ai_suggestion_batch",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_suggestion_user_model_prompt_hash",
                        columnNames = {"user_id", "model", "prompt_version", "input_hash"}
                )
        },
        indexes = {
                @Index(name = "idx_suggestion_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_suggestion_user_status_created", columnList = "user_id, status, created_at")
        }
)
public class StockAiSuggestionBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long suggestionBatchId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne
    @JoinColumn(name = "profile_id", nullable = false)
    private UserInvestmentProfile profile;

    @Column(name = "profile_version", nullable = false)
    private Integer profileVersion;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StockAiSuggestionBatchStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 40)
    private StockAiSuggestionTriggerReason triggerReason;

    @Column(name = "input_hash", length = 64, nullable = false)
    private String inputHash;

    @Column(name = "batch_summary", length = 1000)
    private String batchSummary;

    @Column(name = "analysis_timeframe", length = 10, nullable = false)
    private String analysisTimeframe;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "finish_reason")
    private String finishReason;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
