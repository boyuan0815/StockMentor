package net.boyuan.stockmentor.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshJobStatus;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshTriggeredBy;
import net.boyuan.stockmentor.auth.entity.AppUser;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "ai_suggestion_refresh_job",
        indexes = {
                @Index(name = "idx_ai_refresh_job_started", columnList = "started_at"),
                @Index(name = "idx_ai_refresh_job_status_started", columnList = "status, started_at"),
                @Index(name = "idx_ai_refresh_job_triggered_started", columnList = "triggered_by, started_at")
        }
)
public class AiSuggestionRefreshJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 30)
    private AiSuggestionRefreshTriggeredBy triggeredBy;

    @ManyToOne
    @JoinColumn(name = "triggered_by_user_id")
    private AppUser triggeredByUser;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "processed_users", nullable = false)
    private Integer processedUsers;

    @Column(name = "skipped_users", nullable = false)
    private Integer skippedUsers;

    @Column(name = "success_count", nullable = false)
    private Integer successCount;

    @Column(name = "reused_count", nullable = false)
    private Integer reusedCount;

    @Column(name = "fallback_count", nullable = false)
    private Integer fallbackCount;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AiSuggestionRefreshJobStatus status;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
