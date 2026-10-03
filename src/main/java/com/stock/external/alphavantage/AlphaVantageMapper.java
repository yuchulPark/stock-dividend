package com.stock.external.alphavantage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import com.stock.entity.AssetType;
import com.stock.entity.Currency;
import com.stock.entity.Market;
import com.stock.exception.ApiException;
import com.stock.external.alphavantage.dto.AlphaVantageResponse.*;
import com.stock.external.model.*;
import org.springframework.stereotype.Component;
@Component
public class AlphaVantageMapper {
    public List<AssetData> search(Search response) {
        if (response == null || response.bestMatches() == null) throw ApiException.invalidResponse();
        Map<String, AssetData> result = new LinkedHashMap<>();
        for (Match match : response.bestMatches()) {
            if (match == null || match.region() == null || match.type() == null || match.currency() == null) throw ApiException.invalidResponse();
            if (!"United States".equals(match.region()) || !"USD".equals(match.currency())) continue;
            AssetType type = switch (match.type()) { case "Equity" -> AssetType.STOCK; case "ETF" -> AssetType.ETF; default -> null; };
            if (type == null) continue;
            if (match.symbol() == null || match.symbol().isBlank() || match.symbol().length() > 32
                    || match.name() == null || match.name().isBlank() || match.name().length() > 200) throw ApiException.invalidResponse();
            AssetData data = new AssetData(match.symbol().toUpperCase(Locale.ROOT), match.name(), Market.US, type, Currency.USD);
            AssetData old = result.putIfAbsent(data.ticker(), data);
            if (old != null && !old.equals(data)) throw ApiException.invalidResponse();
        }
        return List.copyOf(result.values());
    }
    public QuoteData quote(Quote response, String ticker) {
        if (response == null || response.quote() == null) throw ApiException.invalidResponse();
        QuoteFields quote = response.quote();
        if (quote.symbol() == null && quote.price() == null && quote.tradingDay() == null) throw ApiException.notFound();
        if (!ticker.equalsIgnoreCase(quote.symbol())) throw ApiException.invalidResponse();
        BigDecimal price = decimal(quote.price());
        if (price.signum() <= 0) throw ApiException.invalidResponse();
        LocalDate asOf = date(quote.tradingDay(), false);
        return new QuoteData(ticker, price, asOf);
    }
    public List<DividendData> dividends(Dividends response, String ticker) {
        if (response == null || !ticker.equalsIgnoreCase(response.symbol()) || response.data() == null) throw ApiException.invalidResponse();
        Map<LocalDate, DividendData> result = new LinkedHashMap<>();
        for (DividendFields row : response.data()) {
            if (row == null) throw ApiException.invalidResponse();
            BigDecimal amount = decimal(row.amount());
            if (amount.signum() < 0) throw ApiException.invalidResponse();
            LocalDate exDate = date(row.exDate(), false);
            LocalDate recordDate = date(row.recordDate(), true);
            LocalDate paymentDate = date(row.paymentDate(), true);
            if (amount.signum() == 0) continue; // Domain stores positive cash distributions only.
            DividendData data = new DividendData(amount, exDate, recordDate, paymentDate, Currency.USD);
            DividendData old = result.putIfAbsent(exDate, data);
            if (old != null && (old.amount().compareTo(data.amount()) != 0
                    || !Objects.equals(old.recordDate(), data.recordDate()) || !Objects.equals(old.paymentDate(), data.paymentDate())))
                throw new ApiException("EXTERNAL_API_AMBIGUOUS_DIVIDEND", "동일 배당락일의 배당 데이터를 안전하게 구분할 수 없습니다.", org.springframework.http.HttpStatus.BAD_GATEWAY);
        }
        return List.copyOf(result.values());
    }
    private static BigDecimal decimal(String value) {
        try {
            BigDecimal amount = new BigDecimal(value).stripTrailingZeros();
            if (amount.scale() > 8 || amount.precision() - amount.scale() > 16) throw ApiException.invalidResponse();
            return amount;
        } catch (NumberFormatException | NullPointerException ex) { throw ApiException.invalidResponse(); }
    }
    private static LocalDate date(String value, boolean nullable) {
        if (value == null || value.isBlank() || "None".equals(value) || "null".equals(value)) {
            if (nullable) return null;
            throw ApiException.invalidResponse();
        }
        try { return LocalDate.parse(value); }
        catch (java.time.format.DateTimeParseException ex) { throw ApiException.invalidResponse(); }
    }
}
