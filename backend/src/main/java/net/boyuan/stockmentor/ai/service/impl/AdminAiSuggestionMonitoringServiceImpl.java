package net.boyuan.stockmentor.ai.service.impl;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.dto.admin.*;
import net.boyuan.stockmentor.ai.entity.AiSuggestionRefreshJob;
import net.boyuan.stockmentor.ai.entity.StockAiSuggestionBatch;
import net.boyuan.stockmentor.ai.entity.StockAiSuggestionItem;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshJobStatus;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshTriggeredBy;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionBatchStatus;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.ai.repository.AiSuggestionRefreshJobRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionBatchRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionItemRepository;
import net.boyuan.stockmentor.ai.service.AdminAiSuggestionMonitoringService;
import net.boyuan.stockmentor.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAiSuggestionMonitoringServiceImpl implements AdminAiSuggestionMonitoringService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<StockAiSuggestionBatchStatus> FAILURE_STATUSES = List.of(
            StockAiSuggestionBatchStatus.FAILED,
            StockAiSuggestionBatchStatus.FALLBACK_CACHED,
            StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED
    );

    private final StockAiSuggestionBatchRepository batchRepository;
    private final StockAiSuggestionItemRepository itemRepository;
    private final AiSuggestionRefreshJobRepository jobRepository;

    @Override
    public AdminPageResponse<AdminAiSuggestionBatchRowResponse> listBatches(
            Long userId,
            String email,
            String status,
            String triggerReason,
            String from,
            String to,
            int page,
            int size
    ) {
        Page<StockAiSuggestionBatch> batches = batchRepository.findAll(
                batchSpec(userId, email, parseBatchStatus(status), parseTriggerReason(triggerReason), parseFrom(from), parseTo(to), null),
                PageRequest.of(Math.max(page, 0), normalizeSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return toPageResponse(batches, toBatchRows(batches.getContent()));
    }

    @Override
    public AdminAiSuggestionBatchDetailResponse getBatch(Long batchId) {
        StockAiSuggestionBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("AI suggestion batch not found"));
        List<StockAiSuggestionItem> items = itemRepository.findBySuggestionBatchOrderByRankNoAsc(batch);
        List<String> symbols = items.stream().map(StockAiSuggestionItem::getSymbol).toList();
        return toBatchDetail(batch, items, symbols);
    }

    @Override
    public AdminPageResponse<AdminAiSuggestionBatchRowResponse> listFailures(
            String from,
            String to,
            String triggerReason,
            int page,
            int size
    ) {
        Page<StockAiSuggestionBatch> batches = batchRepository.findAll(
                batchSpec(null, null, null, parseTriggerReason(triggerReason), parseFrom(from), parseTo(to), FAILURE_STATUSES),
                PageRequest.of(Math.max(page, 0), normalizeSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return toPageResponse(batches, toBatchRows(batches.getContent()));
    }

    @Override
    public AdminAiSuggestionUsageSummaryResponse usageSummary(String from, String to) {
        List<StockAiSuggestionBatch> batches = batchRepository.findAll(
                batchSpec(null, null, null, null, parseFrom(from), parseTo(to), null)
        );

        Map<String, Long> byStatus = batches.stream()
                .collect(Collectors.groupingBy(batch -> batch.getStatus().name(), TreeMap::new, Collectors.counting()));
        Map<String, Long> byTrigger = batches.stream()
                .collect(Collectors.groupingBy(batch -> batch.getTriggerReason().name(), TreeMap::new, Collectors.counting()));

        return new AdminAiSuggestionUsageSummaryResponse(
                (long) batches.size(),
                countStatus(byStatus, StockAiSuggestionBatchStatus.SUCCESS),
                countStatus(byStatus, StockAiSuggestionBatchStatus.FAILED),
                countStatus(byStatus, StockAiSuggestionBatchStatus.FALLBACK_CACHED),
                countStatus(byStatus, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED),
                sumTokens(batches, TokenType.PROMPT),
                sumTokens(batches, TokenType.COMPLETION),
                sumTokens(batches, TokenType.TOTAL),
                grouped(byTrigger),
                grouped(byStatus)
        );
    }

    @Override
    public AdminPageResponse<AiSuggestionRefreshJobResponse> listRefreshJobs(
            String status,
            String triggeredBy,
            String from,
            String to,
            int page,
            int size
    ) {
        Page<AiSuggestionRefreshJob> jobs = jobRepository.findAll(
                jobSpec(parseJobStatus(status), parseTriggeredBy(triggeredBy), parseFrom(from), parseTo(to)),
                PageRequest.of(Math.max(page, 0), normalizeSize(size), Sort.by(Sort.Direction.DESC, "startedAt"))
        );
        return toPageResponse(jobs, jobs.getContent().stream().map(this::toJobResponse).toList());
    }

    @Override
    public AiSuggestionRefreshJobResponse getRefreshJob(Long jobId) {
        return jobRepository.findById(jobId)
                .map(this::toJobResponse)
                .orElseThrow(() -> new ResourceNotFoundException("AI suggestion refresh job not found"));
    }

    private List<AdminAiSuggestionBatchRowResponse> toBatchRows(List<StockAiSuggestionBatch> batches) {
        if (batches.isEmpty()) {
            return List.of();
        }
        List<Long> batchIds = batches.stream().map(StockAiSuggestionBatch::getSuggestionBatchId).toList();
        Map<Long, List<StockAiSuggestionItem>> itemsByBatchId = itemRepository
                .findBySuggestionBatchSuggestionBatchIdInOrderBySuggestionBatchSuggestionBatchIdAscRankNoAsc(batchIds)
                .stream()
                .collect(Collectors.groupingBy(item -> item.getSuggestionBatch().getSuggestionBatchId(), LinkedHashMap::new, Collectors.toList()));

        return batches.stream()
                .map(batch -> {
                    List<StockAiSuggestionItem> items = itemsByBatchId.getOrDefault(batch.getSuggestionBatchId(), List.of());
                    return toBatchRow(batch, items.stream().map(StockAiSuggestionItem::getSymbol).toList(), items.size());
                })
                .toList();
    }

    private AdminAiSuggestionBatchDetailResponse toBatchDetail(
            StockAiSuggestionBatch batch,
            List<StockAiSuggestionItem> items,
            List<String> symbols
    ) {
        return new AdminAiSuggestionBatchDetailResponse(
                batch.getSuggestionBatchId(),
                batch.getUser().getUserId(),
                batch.getUser().getEmail(),
                batch.getUser().getUsername(),
                batch.getStatus().name(),
                batch.getTriggerReason().name(),
                batch.getAnalysisTimeframe(),
                batch.getModel(),
                batch.getPromptVersion(),
                batch.getProfileVersion(),
                batch.getInputHash(),
                batch.getPromptTokens(),
                batch.getCompletionTokens(),
                batch.getTotalTokens(),
                batch.getFinishReason(),
                isFallback(batch),
                batch.getErrorMessage(),
                batch.getCreatedAt(),
                batch.getExpiresAt(),
                symbols,
                items.size(),
                items.stream().map(this::toItemResponse).toList()
        );
    }

    private AdminAiSuggestionBatchRowResponse toBatchRow(StockAiSuggestionBatch batch, List<String> symbols, int itemCount) {
        return new AdminAiSuggestionBatchRowResponse(
                batch.getSuggestionBatchId(),
                batch.getUser().getUserId(),
                batch.getUser().getEmail(),
                batch.getStatus().name(),
                batch.getTriggerReason().name(),
                batch.getAnalysisTimeframe(),
                batch.getModel(),
                batch.getPromptVersion(),
                batch.getPromptTokens(),
                batch.getCompletionTokens(),
                batch.getTotalTokens(),
                batch.getFinishReason(),
                isFallback(batch),
                batch.getErrorMessage(),
                batch.getCreatedAt(),
                batch.getExpiresAt(),
                symbols,
                itemCount
        );
    }

    private AdminAiSuggestionItemResponse toItemResponse(StockAiSuggestionItem item) {
        return new AdminAiSuggestionItemResponse(
                item.getSuggestionItemId(),
                item.getSymbol(),
                item.getRankNo(),
                item.getMatchScore(),
                item.getRiskLevel(),
                item.getSuggestionLabel(),
                item.getShortReason(),
                item.getStatus().name(),
                item.getAnalysisSnapshot().getAnalysisSnapshotId(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private AiSuggestionRefreshJobResponse toJobResponse(AiSuggestionRefreshJob job) {
        return new AiSuggestionRefreshJobResponse(
                job.getJobId(),
                job.getStatus().name(),
                job.getTriggeredBy().name(),
                job.getTriggeredByUser() == null ? null : job.getTriggeredByUser().getUserId(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getProcessedUsers(),
                job.getSkippedUsers(),
                job.getSuccessCount(),
                job.getReusedCount(),
                job.getFallbackCount(),
                job.getFailedCount(),
                job.getMessage()
        );
    }

    private Specification<StockAiSuggestionBatch> batchSpec(
            Long userId,
            String email,
            StockAiSuggestionBatchStatus status,
            StockAiSuggestionTriggerReason triggerReason,
            LocalDateTime from,
            LocalDateTime to,
            Collection<StockAiSuggestionBatchStatus> statuses
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(builder.equal(root.get("user").get("userId"), userId));
            }
            if (email != null && !email.isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("user").get("email")), "%" + email.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (triggerReason != null) {
                predicates.add(builder.equal(root.get("triggerReason"), triggerReason));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<AiSuggestionRefreshJob> jobSpec(
            AiSuggestionRefreshJobStatus status,
            AiSuggestionRefreshTriggeredBy triggeredBy,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (triggeredBy != null) {
                predicates.add(builder.equal(root.get("triggeredBy"), triggeredBy));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("startedAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("startedAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private <T> AdminPageResponse<T> toPageResponse(Page<?> page, List<T> content) {
        return new AdminPageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private boolean isFallback(StockAiSuggestionBatch batch) {
        return batch.getStatus() == StockAiSuggestionBatchStatus.FALLBACK_CACHED
                || batch.getStatus() == StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED;
    }

    private long countStatus(Map<String, Long> grouped, StockAiSuggestionBatchStatus status) {
        return grouped.getOrDefault(status.name(), 0L);
    }

    private long sumTokens(List<StockAiSuggestionBatch> batches, TokenType tokenType) {
        return batches.stream()
                .mapToLong(batch -> switch (tokenType) {
                    case PROMPT -> nullToZero(batch.getPromptTokens());
                    case COMPLETION -> nullToZero(batch.getCompletionTokens());
                    case TOTAL -> nullToZero(batch.getTotalTokens());
                })
                .sum();
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private List<AdminAiSuggestionGroupedCountResponse> grouped(Map<String, Long> grouped) {
        return grouped.entrySet().stream()
                .map(entry -> new AdminAiSuggestionGroupedCountResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private StockAiSuggestionBatchStatus parseBatchStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return StockAiSuggestionBatchStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid AI suggestion batch status: " + value);
        }
    }

    private StockAiSuggestionTriggerReason parseTriggerReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return StockAiSuggestionTriggerReason.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid AI suggestion trigger reason: " + value);
        }
    }

    private AiSuggestionRefreshJobStatus parseJobStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AiSuggestionRefreshJobStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid AI suggestion refresh job status: " + value);
        }
    }

    private AiSuggestionRefreshTriggeredBy parseTriggeredBy(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AiSuggestionRefreshTriggeredBy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid AI suggestion refresh trigger source: " + value);
        }
    }

    private LocalDateTime parseFrom(String value) {
        return parseDateTime(value, false);
    }

    private LocalDateTime parseTo(String value) {
        return parseDateTime(value, true);
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 10) {
                LocalDate date = LocalDate.parse(trimmed);
                return endOfDay ? date.atTime(LocalTime.MAX) : date.atStartOfDay();
            }
            return LocalDateTime.parse(trimmed);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date/time filter: " + value);
        }
    }

    private enum TokenType {
        PROMPT,
        COMPLETION,
        TOTAL
    }
}
