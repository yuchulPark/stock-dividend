package com.stock.external;
import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.entity.AssetType;
import com.stock.external.alphavantage.AlphaVantageMapper;
import com.stock.external.alphavantage.dto.AlphaVantageResponse.*;
import com.stock.exception.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AlphaVantageMapperTests {
    private final ObjectMapper json = new ObjectMapper();
    private final AlphaVantageMapper mapper = new AlphaVantageMapper();
    @Test void mapsOfficialQuoteFieldNamesWithoutFloatingPoint() throws Exception {
        Quote quote = json.readValue("""
            {"Global Quote":{"01. symbol":"SCHD","05. price":"31.50123456","07. latest trading day":"2026-10-01"}}
            """, Quote.class);
        assertThat(mapper.quote(quote, "SCHD").price()).isEqualByComparingTo(new BigDecimal("31.50123456"));
    }
    @Test void filtersForeignAssetsAndMapsStockAndEtf() throws Exception {
        Search search = json.readValue("""
            {"bestMatches":[
              {"1. symbol":"BA","2. name":"Boeing Company","3. type":"Equity","4. region":"United States","8. currency":"USD"},
              {"1. symbol":"BA.LON","2. name":"BAE","3. type":"Equity","4. region":"United Kingdom","8. currency":"GBX"},
              {"1. symbol":"SCHD","2. name":"Schwab ETF","3. type":"ETF","4. region":"United States","8. currency":"USD"}]}
            """, Search.class);
        assertThat(mapper.search(search)).hasSize(2);
        assertThat(mapper.search(search).get(1).assetType()).isEqualTo(AssetType.ETF);
    }
    @Test void nullableDatesAndEquivalentDuplicateAmountsAreSupported() throws Exception {
        Dividends data = json.readValue("""
            {"symbol":"IBM","data":[
              {"ex_dividend_date":"2026-08-10","record_date":"None","payment_date":null,"amount":"1.69000000"},
              {"ex_dividend_date":"2026-08-10","record_date":null,"amount":"1.69"}]}
            """, Dividends.class);
        var rows = mapper.dividends(data, "IBM");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).amount()).isEqualByComparingTo("1.69");
        assertThat(rows.get(0).recordDate()).isNull();
    }
    @Test void conflictingSameDateDistributionsFailWithoutInventingAnIdentity() {
        var data = new Dividends("IBM", List.of(new DividendFields("2026-08-10", null, null, "1.69"),
                new DividendFields("2026-08-10", null, null, "0.50")));
        assertThatThrownBy(() -> mapper.dividends(data, "IBM")).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("EXTERNAL_API_AMBIGUOUS_DIVIDEND");
    }
    @Test void rejectsMissingFieldsAndExcessPrecision() {
        assertThatThrownBy(() -> mapper.quote(new Quote(new QuoteFields("IBM", "1.123456789", "2026-10-01")), "IBM"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> mapper.dividends(new Dividends("IBM", null), "IBM")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> mapper.quote(new Quote(new QuoteFields("MSFT", "1.0", "2026-10-01")), "IBM"))
                .isInstanceOf(ApiException.class);
    }
}
