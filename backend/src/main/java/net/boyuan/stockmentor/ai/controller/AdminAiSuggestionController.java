package net.boyuan.stockmentor.ai.controller;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.admin.AdminTokenValidator;
import net.boyuan.stockmentor.ai.dto.admin.*;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshTriggeredBy;
import net.boyuan.stockmentor.ai.service.AdminAiSuggestionMonitoringService;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionScheduledRefreshService;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai-suggestions")
@RequiredArgsConstructor
public class AdminAiSuggestionController {
    private final AdminTokenValidator adminTokenValidator;
    private final AdminAiSuggestionMonitoringService monitoringService;
    private final StockAiSuggestionScheduledRefreshService scheduledRefreshService;
    private final CurrentUserService currentUserService;

    @GetMapping("/batches")
    public AdminPageResponse<AdminAiSuggestionBatchRowResponse> listBatches(
            @RequestHeader(name = "X-Admin-Token", required = false) String adminToken,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerReason,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        adminTokenValidator.validate(adminToken);
        return monitoringService.listBatches(userId, email, status, triggerReason, from, to, page, size);
    }

    @GetMapping("/batches/{batchId}")
    public AdminAiSuggestionBatchDetailResponse getBatch(
            @RequestHeader(name = "X-Admin-Token", required = false) String adminToken,
            @PathVariable Long batchId
    ) {
        adminTokenValidator.validate(adminToken);
        return monitoringService.getBatch(batchId);
    }

    @GetMapping("/failures")
    public AdminPageResponse<AdminAiSuggestionBatchRowResponse> listFailures(
            @RequestHeader(name = "X-Admin-Token", required = false) String adminToken,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String triggerReason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        adminTokenValidator.validate(adminToken);
        return monitoringService.listFailures(from, to, triggerReason, page, size);
    }

    @GetMapping("/usage-summary")
    public AdminAiSuggestionUsageSummaryResponse usageSummary(
            @RequestHeader(name = "X-Admin-Token", required = false) String adminToken,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        adminTokenValidator.validate(adminToken);
        return monitoringService.usageSummary(from, to);
    }

    @PostMapping("/scheduled-refresh/run")
    public AiSuggestionRefreshJobResponse runScheduledRefresh(
            @RequestHeader(name = "X-Admin-Token", required = false) String adminToken
    ) {
        adminTokenValidator.validate(adminToken);
        return scheduledRefreshService.runScheduledRefresh(
                AiSuggestionRefreshTriggeredBy.ADMIN_MANUAL,
                currentUserService.getCurrentUser()
        );
    }

    @GetMapping("/refresh-jobs")
    public AdminPageResponse<AiSuggestionRefreshJobResponse> listRefreshJobs(
            @RequestHeader(name = "X-Admin-Token", required = false) String adminToken,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggeredBy,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        adminTokenValidator.validate(adminToken);
        return monitoringService.listRefreshJobs(status, triggeredBy, from, to, page, size);
    }

    @GetMapping("/refresh-jobs/{jobId}")
    public AiSuggestionRefreshJobResponse getRefreshJob(
            @RequestHeader(name = "X-Admin-Token", required = false) String adminToken,
            @PathVariable Long jobId
    ) {
        adminTokenValidator.validate(adminToken);
        return monitoringService.getRefreshJob(jobId);
    }
}
