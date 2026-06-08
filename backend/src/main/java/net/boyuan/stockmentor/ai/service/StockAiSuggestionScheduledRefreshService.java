package net.boyuan.stockmentor.ai.service;

import net.boyuan.stockmentor.ai.dto.admin.AiSuggestionRefreshJobResponse;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshTriggeredBy;
import net.boyuan.stockmentor.auth.entity.AppUser;

public interface StockAiSuggestionScheduledRefreshService {
    AiSuggestionRefreshJobResponse runScheduledRefresh(AiSuggestionRefreshTriggeredBy triggeredBy, AppUser adminUserOrNull);
}
