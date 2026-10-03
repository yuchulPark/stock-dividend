package com.stock.service;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.config.CacheProperties;
import com.stock.entity.AssetType;
import com.stock.entity.Currency;
import com.stock.entity.Market;
import com.stock.entity.StockAsset;
import com.stock.entity.AssetSearchCache;
import com.stock.external.market.MarketDataProvider;
import com.stock.external.market.MarketDataProviderRegistry;
import com.stock.external.dividend.DividendDataProvider;
import com.stock.exception.ApiException;
import com.stock.repository.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AssetSyncServiceTests {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
    private final StockAssetRepository assets = mock(StockAssetRepository.class);
    private final DividendRepository dividends = mock(DividendRepository.class);
    private final AssetSearchCacheRepository searches = mock(AssetSearchCacheRepository.class);
    private final MarketDataProvider market = mock(MarketDataProvider.class);
    private final DividendDataProvider dividend = mock(DividendDataProvider.class);
    private final AssetPersistenceService persistence = mock(AssetPersistenceService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private AssetSyncService service() {
        when(market.supports(Market.US)).thenReturn(true);
        when(dividend.supports(Market.US)).thenReturn(true);
        when(dividend.supports(eq(Market.US), any())).thenReturn(true);
        doCallRealMethod().when(market).refreshQuote(any(), anyString());
        return new AssetSyncService(assets, dividends, searches, new MarketDataProviderRegistry(List.of(market), List.of(dividend)), persistence,
                new RefreshCoordinator(), new CacheProperties(), mapper, clock);
    }
    private StockAsset asset(LocalDateTime priceTime) {
        StockAsset a = new StockAsset("SCHD", "Schwab ETF", Market.US, AssetType.ETF, Currency.USD);
        a.updateMetadata("Schwab ETF", AssetType.ETF, Currency.USD, LocalDateTime.now(clock));
        a.updatePrice(new BigDecimal("31.50"), LocalDate.of(2026, 10, 1), priceTime);
        when(assets.findByMarketAndTicker(Market.US, "SCHD")).thenReturn(java.util.Optional.of(a));
        return a;
    }
    @Test void freshAssetAvoidsAllExternalCalls() {
        var service = service(); asset(LocalDateTime.now(clock));
        assertThat(service.getAsset(Market.US, "schd").dataFresh()).isTrue();
        verify(market, never()).getQuote(any(), anyString());
        verify(market, never()).searchAssets(any(), anyString());
    }
    @Test void stalePriceFallsBackAndDoesNotChangeTimestamp() {
        var service = service(); var a = asset(LocalDateTime.now(clock).minusHours(2));
        when(market.getQuote(Market.US, "SCHD")).thenThrow(ApiException.rateLimit());
        var response = service.getAsset(Market.US, "SCHD");
        assertThat(response.dataFresh()).isFalse();
        assertThat(response.currentPrice()).isEqualByComparingTo("31.50");
        assertThat(response.priceUpdatedAt()).isEqualTo(a.getPriceUpdatedAt());
        verifyNoInteractions(persistence);
    }
    @Test void successfullyEmptyDividendHistoryIsCachedAndCanFallBack() {
        var service = service(); var a = asset(LocalDateTime.now(clock));
        a.markDividendsSynced(LocalDateTime.now(clock));
        when(dividends.findByStockAssetIdOrderByExDividendDateDesc(null)).thenReturn(List.of());
        assertThat(service.getDividends(Market.US, "SCHD").dataFresh()).isTrue();
        verify(dividend, never()).getDividends(any(), anyString());
        a.markDividendsSynced(LocalDateTime.now(clock).minusDays(2));
        when(dividend.getDividends(Market.US, "SCHD")).thenThrow(ApiException.rateLimit());
        assertThat(service.getDividends(Market.US, "SCHD").dataFresh()).isFalse();
    }
    @Test void concurrentSearchesReusePersistedResponse() throws Exception {
        var service = service();
        var cacheEntry = new java.util.concurrent.atomic.AtomicReference<AssetSearchCache>();
        when(searches.findById("US:SCHD")).thenAnswer(call -> java.util.Optional.ofNullable(cacheEntry.get()));
        when(searches.saveAndFlush(any())).thenAnswer(call -> { cacheEntry.set(call.getArgument(0)); return cacheEntry.get(); });
        when(market.searchAssets(Market.US, "SCHD")).thenReturn(List.of());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var futures = java.util.stream.IntStream.range(0, 20).mapToObj(i -> pool.submit(() -> service.search(Market.US, "SCHD"))).toList();
            for (var future : futures) assertThat(future.get(5, TimeUnit.SECONDS).dataFresh()).isTrue();
        } finally { pool.shutdownNow(); }
        verify(market, times(1)).searchAssets(Market.US, "SCHD");
    }
    @Test void unsupportedMarketDoesNotCallProvider() {
        var service = service();
        assertThatThrownBy(() -> service.getAsset(Market.KR, "005930")).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("MARKET_NOT_SUPPORTED");
        verify(market, never()).getQuote(any(), anyString());
    }
    @Test void concurrentStaleAssetRequestsRefreshPriceOnlyOnce() throws Exception {
        var service = service(); var a = asset(LocalDateTime.now(clock).minusHours(2));
        var quote = new com.stock.external.model.QuoteData("SCHD", new BigDecimal("32.12345678"), LocalDate.of(2026, 10, 1));
        when(market.getQuote(Market.US, "SCHD")).thenReturn(quote);
        when(persistence.price(isNull(), any())).thenAnswer(call -> {
            a.updatePrice(quote.price(), quote.asOfDate(), LocalDateTime.now(clock)); return a;
        });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var futures = java.util.stream.IntStream.range(0, 20).mapToObj(i -> pool.submit(() -> service.getAsset(Market.US, "SCHD"))).toList();
            for (var future : futures) assertThat(future.get(5, TimeUnit.SECONDS).currentPrice()).isEqualByComparingTo("32.12345678");
        } finally { pool.shutdownNow(); }
        verify(market, times(1)).getQuote(Market.US, "SCHD");
    }
    @Test void americanRefreshStillUpdatesMetadataPriceAndDividendHistory() {
        var service = service(); var a = asset(LocalDateTime.now(clock));
        a.markDividendsSynced(LocalDateTime.now(clock));
        var data = new com.stock.external.model.AssetData("SCHD", "Schwab ETF", Market.US, AssetType.ETF, Currency.USD);
        when(market.refreshSearchAssets(Market.US, "SCHD")).thenReturn(List.of(data));
        when(persistence.metadata(data)).thenReturn(a);
        var quote = new com.stock.external.model.QuoteData("SCHD", new BigDecimal("32"), LocalDate.of(2026, 10, 2));
        when(market.getQuote(Market.US, "SCHD")).thenReturn(quote);
        when(persistence.price(isNull(), eq(quote))).thenAnswer(call -> {
            a.updatePrice(quote.price(), quote.asOfDate(), LocalDateTime.now(clock)); return a;
        });
        when(dividend.source()).thenReturn(com.stock.entity.DataSourceType.ALPHA_VANTAGE);
        when(dividend.getDividends(Market.US, "SCHD")).thenReturn(List.of());
        when(assets.findById(isNull())).thenReturn(java.util.Optional.of(a));
        when(dividends.findByStockAssetIdOrderByExDividendDateDesc(null)).thenReturn(List.of());
        var response = service.refresh(Market.US, "SCHD");
        assertThat(response.asset().currentPrice()).isEqualByComparingTo("32");
        assertThat(response.dividendDataFresh()).isTrue();
        verify(market).refreshSearchAssets(Market.US, "SCHD");
        verify(persistence).dividends(null, com.stock.entity.DataSourceType.ALPHA_VANTAGE, List.of());
    }
}
