package net.boyuan.stockmentor.market.stock.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record BackfillResultDto(
        String jobType,
        List<String> symbols,
        LocalDate startDate,
        LocalDate endDate,
        int savedRows,
        int skippedRows,
        List<String> messages
) {
    public static Builder builder(String jobType) {
        return new Builder(jobType);
    }

    public static class Builder {
        private final String jobType;
        private final List<String> symbols = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();
        private LocalDate startDate;
        private LocalDate endDate;
        private int savedRows;
        private int skippedRows;

        public Builder(String jobType) {
            this.jobType = jobType;
        }

        public Builder symbols(List<String> symbols) {
            this.symbols.clear();
            this.symbols.addAll(symbols);
            return this;
        }

        public Builder startDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder endDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder savedRows(int savedRows) {
            this.savedRows = savedRows;
            return this;
        }

        public Builder skippedRows(int skippedRows) {
            this.skippedRows = skippedRows;
            return this;
        }

        public Builder addSavedRows(int savedRows) {
            this.savedRows += savedRows;
            return this;
        }

        public Builder addSkippedRows(int skippedRows) {
            this.skippedRows += skippedRows;
            return this;
        }

        public Builder addMessage(String message) {
            this.messages.add(message);
            return this;
        }

        public BackfillResultDto build() {
            return new BackfillResultDto(
                    jobType,
                    List.copyOf(symbols),
                    startDate,
                    endDate,
                    savedRows,
                    skippedRows,
                    List.copyOf(messages)
            );
        }
    }
}
