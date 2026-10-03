package com.stock.external.alphavantage.dto;
import java.util.List;
import com.fasterxml.jackson.annotation.*;

/** Field names verified against the official demo responses on 2026-10-02. */
public final class AlphaVantageResponse {
    private AlphaVantageResponse() {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Search(List<Match> bestMatches) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(@JsonProperty("1. symbol") String symbol,
            @JsonProperty("2. name") String name, @JsonProperty("3. type") String type,
            @JsonProperty("4. region") String region, @JsonProperty("8. currency") String currency) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(@JsonProperty("Global Quote") QuoteFields quote) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteFields(@JsonProperty("01. symbol") String symbol,
            @JsonProperty("05. price") String price, @JsonProperty("07. latest trading day") String tradingDay) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dividends(String symbol, List<DividendFields> data) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DividendFields(@JsonProperty("ex_dividend_date") String exDate,
            @JsonProperty("record_date") String recordDate, @JsonProperty("payment_date") String paymentDate,
            String amount) {}
}
