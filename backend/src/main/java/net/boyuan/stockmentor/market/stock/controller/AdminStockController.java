package net.boyuan.stockmentor.market.stock.controller;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.common.util.StockMetadata;
import net.boyuan.stockmentor.market.stock.dto.AdminBackfillRequest;
import net.boyuan.stockmentor.market.stock.dto.BackfillResultDto;
import net.boyuan.stockmentor.market.stock.service.StockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/stocks")
@RequiredArgsConstructor
public class AdminStockController {
    private final StockService stockService;

    @Value("${stockmentor.admin.token:}")
    private String adminToken;

    @PostMapping("/backfill")
    public ResponseEntity<BackfillResultDto> backfill(
            @RequestHeader(name = "X-Admin-Token", required = false) String providedToken,
            @RequestBody AdminBackfillRequest request
    ) {
        validateAdminToken(providedToken);

        String symbols = resolveSymbols(request.symbols());
        String type = request.type() == null ? "DAILY_RANGE" : request.type().trim().toUpperCase();

        return switch (type) {
            case "INTRADAY_DATE" -> {
                if (request.date() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required for INTRADAY_DATE");
                }
                yield ResponseEntity.ok(stockService.backfillIntradayForDate(symbols, request.date()));
            }
            case "DAILY_MISSING" -> {
                LocalDate endDate = request.endDate() == null ? LocalDate.now().minusDays(1) : request.endDate();
                LocalDate startDate = request.startDate() == null ? endDate.minusMonths(3) : request.startDate();
                yield ResponseEntity.ok(stockService.backfillMissingDaily(symbols, startDate, endDate));
            }
            case "CLEANUP_1MIN" -> ResponseEntity.ok(stockService.cleanupOldIntradayData(14));
            case "DAILY_RANGE" -> {
                if (request.startDate() == null || request.endDate() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required for DAILY_RANGE");
                }
                yield ResponseEntity.ok(stockService.backfillDailyRange(symbols, request.startDate(), request.endDate()));
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported backfill type: " + request.type());
        };
    }

    private void validateAdminToken(String providedToken) {
        if (adminToken == null || adminToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Admin token is not configured");
        }
        if (providedToken == null || !adminToken.equals(providedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
        }
    }

    private String resolveSymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return StockMetadata.SYMBOLS;
        }
        return String.join(",", symbols);
    }
}
