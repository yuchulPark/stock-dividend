package com.stock.repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import com.stock.entity.AssetType;
import com.stock.entity.Currency;
import com.stock.entity.DataSourceType;
import com.stock.entity.Dividend;
import com.stock.entity.Market;
import com.stock.entity.StockAsset;
import com.stock.external.model.DividendData;
import com.stock.service.AssetPersistenceService;
import com.stock.service.ProviderCallBudgetService;
import com.stock.config.AlphaVantageProperties;
import com.stock.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest(properties = "external.alpha-vantage.daily-limit=2")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({AssetPersistenceService.class, ProviderCallBudgetService.class, PostgresRepositoryTests.TestClock.class})
class PostgresRepositoryTests {
    @TestConfiguration static class TestClock {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean AlphaVantageProperties alphaProperties() { var p = new AlphaVantageProperties(); p.setDailyLimit(2); return p; }
    }
    @Autowired StockAssetRepository assets;
    @Autowired DividendRepository dividends;
    @Autowired AssetPersistenceService persistence;
    @Autowired TestEntityManager entityManager;
    @Autowired ProviderCallBudgetService budget;
    @Autowired ProviderCallBudgetRepository budgets;
    @Test void dailyBudgetPreventsExtraReservations() {
        budget.reserve(); budget.reserve();
        assertThatThrownBy(budget::reserve).isInstanceOf(ApiException.class).extracting("code").isEqualTo("EXTERNAL_API_RATE_LIMIT");
        assertThat(budgets.findById(LocalDate.now(ZoneOffset.UTC)).orElseThrow().getUsedCalls()).isEqualTo(2);
    }
    private StockAsset asset() {
        return assets.saveAndFlush(new StockAsset("SCHD", "Schwab ETF", Market.US, AssetType.ETF, Currency.USD));
    }
    @Test void marketTickerSearchAndExactNumericStorage() {
        StockAsset asset = asset();
        asset.updatePrice(new BigDecimal("31.50123456"), LocalDate.of(2026, 10, 1), LocalDateTime.now());
        assets.saveAndFlush(asset);
        entityManager.clear();
        assertThat(assets.existsByMarketAndTicker(Market.US, "SCHD")).isTrue();
        assertThat(assets.search(Market.US, "schw")).hasSize(1);
        assertThat(assets.findByMarketAndTicker(Market.US, "SCHD").orElseThrow().getCurrentPrice())
                .isEqualByComparingTo("31.50123456");
        assertThat(asset.getCreatedAt()).isNotNull();
    }
    @Test void repeatedSyncUpdatesAmountWithoutDuplicateAndPreservesNullDates() {
        var asset = asset(); var date = LocalDate.of(2026, 9, 1);
        persistence.dividends(asset.getId(), DataSourceType.ALPHA_VANTAGE,
                List.of(new DividendData(new BigDecimal("0.25"), date, null, null, Currency.USD)));
        persistence.dividends(asset.getId(), DataSourceType.ALPHA_VANTAGE,
                List.of(new DividendData(new BigDecimal("0.25123456"), date, null, date.plusDays(10), Currency.USD)));
        entityManager.flush(); entityManager.clear();
        var rows = dividends.findByStockAssetIdOrderByExDividendDateDesc(asset.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDividendPerShare()).isEqualByComparingTo("0.25123456");
        assertThat(rows.get(0).getRecordDate()).isNull();
        assertThat(rows.get(0).getExternalUpdatedAt()).isNull();
        assertThat(assets.findById(asset.getId()).orElseThrow().getDividendSyncedAt()).isNotNull();
    }
    @Test void databaseRejectsDuplicateDividendEvenWhenAmountChanges() {
        var asset = asset(); var date = LocalDate.of(2026, 9, 1);
        Dividend first = new Dividend(asset, date, DataSourceType.ALPHA_VANTAGE);
        first.update(new BigDecimal("0.25"), null, null, Currency.USD); dividends.saveAndFlush(first);
        Dividend second = new Dividend(asset, date, DataSourceType.ALPHA_VANTAGE);
        second.update(new BigDecimal("0.30"), null, null, Currency.USD);
        assertThatThrownBy(() -> dividends.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void databaseRejectsDuplicateAssetInSameMarket() {
        asset();
        assertThatThrownBy(this::asset).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void koreanMetadataAndPriceUpsertPreservesLeadingZerosAndMarketIsolation() {
        var korean = new com.stock.external.model.AssetData("005930", "삼성전자", Market.KR, AssetType.STOCK, Currency.KRW);
        StockAsset asset = persistence.metadata(korean);
        StockAsset repeated = persistence.metadata(korean);
        assertThat(repeated.getId()).isEqualTo(asset.getId());
        persistence.price(asset.getId(), new com.stock.external.model.QuoteData("005930", new BigDecimal("85000"), LocalDate.of(2026,10,1)));
        assets.saveAndFlush(new StockAsset("005930", "Different US asset", Market.US, AssetType.STOCK, Currency.USD));
        entityManager.clear();
        StockAsset loaded = assets.findByMarketAndTicker(Market.KR, "005930").orElseThrow();
        assertThat(loaded.getTicker()).isEqualTo("005930");
        assertThat(loaded.getCurrency()).isEqualTo(Currency.KRW);
        assertThat(loaded.getCurrentPrice()).isEqualByComparingTo("85000");
        assertThat(loaded.getPriceAsOfDate()).isEqualTo(LocalDate.of(2026,10,1));
        assertThat(assets.findByMarketAndTicker(Market.US, "005930").orElseThrow().getId()).isNotEqualTo(asset.getId());
    }
    private DividendData report(String amount, int year, String filing) {
        return new DividendData(new BigDecimal(amount), null, null, null, Currency.KRW, year, "11011", filing, "보통주");
    }
    @Test void reportUpsertRetainsNoEventDatesAndCorrectionReplacesSameYear() {
        var asset = persistence.metadata(new com.stock.external.model.AssetData("005930", "삼성전자", Market.KR, AssetType.STOCK, Currency.KRW));
        persistence.dividends(asset.getId(), DataSourceType.OPEN_DART, List.of(report("1444",2025,"20260301000001"),report("1400",2024,"20250301000001")));
        persistence.dividends(asset.getId(), DataSourceType.OPEN_DART, List.of(report("1450",2025,"20260401000001")));
        entityManager.clear();
        var rows = dividends.findByStockAssetIdOrderByExDividendDateDesc(asset.getId());
        assertThat(rows).hasSize(2); assertThat(rows.get(0).getBusinessYear()).isEqualTo(2025);
        assertThat(rows.get(0).getDividendPerShare()).isEqualByComparingTo("1450");
        assertThat(rows.get(0).getFilingNumber()).isEqualTo("20260401000001");
        assertThat(rows.get(0).getExDividendDate()).isNull(); assertThat(rows.get(0).getPaymentDate()).isNull();
    }
    @Test void correctedZeroReportRemovesPreviousAmountWithoutStoringZero() {
        var asset = persistence.metadata(new com.stock.external.model.AssetData("005930", "삼성전자", Market.KR, AssetType.STOCK, Currency.KRW));
        persistence.dividends(asset.getId(), DataSourceType.OPEN_DART, List.of(report("1444",2025,"20260301000001")));
        persistence.dividends(asset.getId(), DataSourceType.OPEN_DART, List.of(report("0",2025,"20260401000001")));
        assertThat(dividends.findByStockAssetIdOrderByExDividendDateDesc(asset.getId())).isEmpty();
    }
    @Test void databaseRejectsDuplicateReportDespiteDifferentFilingNumber() {
        var asset = asset();
        var first = new Dividend(asset,null,DataSourceType.OPEN_DART); first.report(2025,"11011","20260301000001","보통주");
        first.update(new BigDecimal("1444"),null,null,Currency.KRW); dividends.saveAndFlush(first);
        var second = new Dividend(asset,null,DataSourceType.OPEN_DART); second.report(2025,"11011","20260401000001","보통주");
        second.update(new BigDecimal("1450"),null,null,Currency.KRW);
        assertThatThrownBy(() -> dividends.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void databaseStillRejectsAmericanDividendWithoutExDate() {
        var invalid = new Dividend(asset(),null,DataSourceType.ALPHA_VANTAGE); invalid.update(new BigDecimal("1"),null,null,Currency.USD);
        assertThatThrownBy(() -> dividends.saveAndFlush(invalid)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
