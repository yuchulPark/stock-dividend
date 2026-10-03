package com.stock.external.krx;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.stock.entity.*;
import com.stock.exception.ApiException;
import com.stock.external.krx.dto.KrxResponse;
import com.stock.external.model.*;
import org.springframework.stereotype.Component;
@Component
public class KrxMapper {
    public record Instrument(AssetData asset, String exchange, String shareClass) {}
    public Instrument instrument(KrxResponse.Row row, KrxApiClient.Api api) {
        boolean etf = api == KrxApiClient.Api.ETF_PRICE;
        String ticker = etf ? row.code() : row.shortCode();
        if (ticker == null || !ticker.matches("[0-9A-Z]{6}") || row.name() == null || row.name().isBlank()
                || row.name().length() > 200) throw ApiException.invalidResponse();
        String exchange = etf ? "ETF" : api == KrxApiClient.Api.KOSPI_INFO ? "KOSPI" : "KOSDAQ";
        if (!etf && !exchange.equals(row.market())) throw ApiException.invalidResponse();
        return new Instrument(new AssetData(ticker, row.name(), Market.KR, etf ? AssetType.ETF : AssetType.STOCK, Currency.KRW), exchange, row.shareClass());
    }
    public QuoteData quote(KrxResponse.Row row, String ticker, LocalDate requested) {
        try {
            LocalDate date = LocalDate.parse(row.date(), DateTimeFormatter.BASIC_ISO_DATE);
            String raw = row.close();
            if (!ticker.equals(row.code()) || !date.equals(requested) || raw == null
                    || !raw.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")) throw ApiException.invalidResponse();
            BigDecimal price = new BigDecimal(raw.replace(",", ""));
            if (price.signum() <= 0 || price.scale() > 8 || price.precision() - price.scale() > 16) throw ApiException.invalidResponse();
            return new QuoteData(ticker, price, date);
        } catch (ApiException ex) { throw ex; }
        catch (Exception ex) { throw ApiException.invalidResponse(); }
    }
}
