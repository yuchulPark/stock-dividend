package com.stock.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.config.CacheProperties;
import com.stock.entity.AssetType;
import com.stock.entity.Currency;
import com.stock.entity.Market;
import com.stock.entity.StockAsset;
import com.stock.exception.ApiException;
import com.stock.external.market.*;
import com.stock.external.model.*;
import com.stock.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KoreanAssetSyncServiceTests {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
    private final StockAssetRepository assets = mock(StockAssetRepository.class);
    private final DividendRepository dividends = mock(DividendRepository.class);
    private final AssetSearchCacheRepository searches = mock(AssetSearchCacheRepository.class);
    private final MarketDataProvider korean = mock(MarketDataProvider.class);
    private final MarketDataProvider american = mock(MarketDataProvider.class);
    private final AssetPersistenceService persistence = mock(AssetPersistenceService.class);
    private final AssetData metadata = new AssetData("005930", "삼성전자", Market.KR, AssetType.STOCK, Currency.KRW);
    private AssetSyncService service() {
        when(korean.supports(Market.KR)).thenReturn(true); when(american.supports(Market.US)).thenReturn(true);
        doCallRealMethod().when(korean).refreshQuote(any(), anyString());
        return new AssetSyncService(assets, dividends, searches,
                new MarketDataProviderRegistry(List.of(korean, american), List.of()), persistence,
                new RefreshCoordinator(), new CacheProperties(), new ObjectMapper(), clock);
    }
    private StockAsset asset(LocalDateTime priceTime) {
        var asset = new StockAsset("005930", "삼성전자", Market.KR, AssetType.STOCK, Currency.KRW);
        asset.updateMetadata("삼성전자", AssetType.STOCK, Currency.KRW, LocalDateTime.now(clock));
        asset.updatePrice(new BigDecimal("85000"), LocalDate.of(2026, 10, 1), priceTime);
        when(assets.findByMarketAndTicker(Market.KR, "005930")).thenReturn(Optional.of(asset)); return asset;
    }
    @Test void freshKoreanPriceAvoidsBothProviders() {
        var service = service(); asset(LocalDateTime.now(clock));
        var result = service.getAsset(Market.KR, "005930");
        assertThat(result.dataFresh()).isTrue(); assertThat(result.currency()).isEqualTo(Currency.KRW);
        assertThat(result.priceType()).isEqualTo("EOD"); assertThat(result.priceAsOfDate()).isEqualTo(LocalDate.of(2026,10,1));
        verify(korean, never()).getQuote(any(), anyString()); verify(american, never()).getQuote(any(), anyString());
    }
    @Test void koreanCacheMissConvertsMetadataThenSavesPrice() {
        var service = service(); var saved = new StockAsset(metadata.ticker(), metadata.name(), metadata.market(), metadata.assetType(), metadata.currency());
        when(korean.searchAssets(Market.KR, "005930")).thenReturn(List.of(metadata));
        when(persistence.metadata(metadata)).thenAnswer(invocation -> {
            saved.updateMetadata(metadata.name(), metadata.assetType(), metadata.currency(), LocalDateTime.now(clock)); return saved;
        });
        var quote = new QuoteData("005930", new BigDecimal("85000"), LocalDate.of(2026, 10, 1));
        when(korean.getQuote(Market.KR, "005930")).thenReturn(quote);
        when(persistence.price(isNull(), eq(quote))).thenAnswer(invocation -> {
            saved.updatePrice(quote.price(), quote.asOfDate(), LocalDateTime.now(clock)); return saved;
        });
        var result = service.getAsset(Market.KR, "005930");
        assertThat(result.currentPrice()).isEqualByComparingTo("85000"); assertThat(result.currency()).isEqualTo(Currency.KRW);
        assertThat(result.dataFresh()).isTrue(); verify(persistence).metadata(metadata); verify(persistence).price(null, quote);
        verify(american, never()).searchAssets(any(), anyString()); verify(american, never()).getQuote(any(), anyString());
    }
    @Test void koreanFailureReturnsPreviousPriceWithOriginalTimestampAndWarning() {
        var service = service(); var saved = asset(LocalDateTime.now(clock).minusHours(7));
        when(korean.getQuote(Market.KR, "005930")).thenThrow(new ApiException("EXTERNAL_API_TIMEOUT", "Timeout", HttpStatus.SERVICE_UNAVAILABLE));
        var result = service.getAsset(Market.KR, "005930");
        assertThat(result.dataFresh()).isFalse(); assertThat(result.currentPrice()).isEqualByComparingTo("85000");
        assertThat(result.priceUpdatedAt()).isEqualTo(saved.getPriceUpdatedAt()); assertThat(result.warningCode()).isEqualTo("EXTERNAL_API_TIMEOUT");
        verifyNoInteractions(persistence);
    }
    @Test void refreshForcesKoreanQuoteButDoesNotFetchDividends() {
        var service = service(); var saved = asset(LocalDateTime.now(clock));
        when(korean.refreshSearchAssets(Market.KR, "005930")).thenReturn(List.of(metadata)); when(persistence.metadata(metadata)).thenReturn(saved);
        var quote = new QuoteData("005930", new BigDecimal("86000"), null);
        when(korean.getQuote(Market.KR, "005930")).thenReturn(quote);
        when(persistence.price(isNull(), eq(quote))).thenAnswer(invocation -> { saved.updatePrice(quote.price(), null, LocalDateTime.now(clock)); return saved; });
        var response = service.refresh(Market.KR, "005930");
        assertThat(response.asset().currentPrice()).isEqualByComparingTo("86000");
        assertThat(response.dividends()).isEmpty(); assertThat(response.dividendDataFresh()).isFalse();
        assertThat(response.dividendWarningCode()).isEqualTo("DIVIDEND_PROVIDER_NOT_IMPLEMENTED"); verifyNoInteractions(dividends);
    }
    @Test void domesticDividendsFailBeforeAnyMetadataNetworkOrDatabaseAccess() {
        var service = service();
        assertThatThrownBy(() -> service.getDividends(Market.KR, "005930")).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("DIVIDEND_PROVIDER_NOT_IMPLEMENTED");
        verifyNoInteractions(assets, dividends, searches, persistence); verify(korean, never()).searchAssets(any(), anyString());
    }
    @Test void invalidDomesticTickerIsRejectedBeforeNetwork() {
        var service = service();
        assertThatThrownBy(() -> service.getAsset(Market.KR, "5930")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(assets); verify(korean, never()).getQuote(any(), anyString());
    }
    @Test void staleFullMasterCanSupplySearchFallbackForUncachedKeyword() {
        var service = service();
        when(korean.searchAssets(Market.KR, "삼성")).thenThrow(ApiException.rateLimit());
        when(assets.search(Market.KR, "삼성")).thenReturn(List.of());
        when(korean.cachedSearchAssets(Market.KR, "삼성")).thenReturn(List.of(metadata));
        var result = service.search(Market.KR, "삼성");
        assertThat(result.dataFresh()).isFalse(); assertThat(result.warningCode()).isEqualTo("EXTERNAL_API_RATE_LIMIT");
        assertThat(result.data()).extracting(a -> a.ticker()).containsExactly("005930"); verify(searches, never()).saveAndFlush(any());
    }
    private final com.stock.external.dividend.DividendDataProvider dart = mock(com.stock.external.dividend.DividendDataProvider.class);
    private AssetSyncService serviceWithDart() {
        when(korean.supports(Market.KR)).thenReturn(true);
        when(dart.supports(Market.KR)).thenReturn(true);
        when(dart.supports(Market.KR,AssetType.STOCK)).thenReturn(true);
        return new AssetSyncService(assets, dividends, searches,new MarketDataProviderRegistry(List.of(korean),List.of(dart)),
                persistence,new RefreshCoordinator(),new CacheProperties(),new ObjectMapper(),clock);
    }
    @Test void openDartFailureReturnsStoredReportWithStaleMetadata() {
        var service = serviceWithDart(); var saved = asset(LocalDateTime.now(clock));
        saved.markDividendsSynced(LocalDateTime.now(clock).minusDays(2));
        var report = new com.stock.entity.Dividend(saved,null,com.stock.entity.DataSourceType.OPEN_DART);
        report.report(2025,"11011","20260301000001","보통주"); report.update(new BigDecimal("1444"),null,null,Currency.KRW);
        when(dividends.findByStockAssetIdOrderByExDividendDateDesc(null)).thenReturn(List.of(report));
        when(dart.getDividends(Market.KR,"005930")).thenThrow(ApiException.rateLimit());
        var result = service.getDividends(Market.KR,"005930");
        assertThat(result.dataFresh()).isFalse(); assertThat(result.warningCode()).isEqualTo("EXTERNAL_API_RATE_LIMIT");
        assertThat(result.data().get(0).dataType()).isEqualTo("REPORT");
        assertThat(result.data().get(0).businessYear()).isEqualTo(2025);
        assertThat(result.data().get(0).exDividendDate()).isNull(); verifyNoInteractions(persistence);
    }
    @Test void domesticEtfDividendFailsWithoutOpenDartCall() {
        var service = serviceWithDart();
        when(assets.findByMarketAndTicker(Market.KR,"490590")).thenReturn(Optional.of(new StockAsset("490590","공식 ETF",Market.KR,AssetType.ETF,Currency.KRW)));
        assertThatThrownBy(() -> service.getDividends(Market.KR,"490590")).extracting("code").isEqualTo("DIVIDEND_DATA_NOT_SUPPORTED");
        verify(dart, never()).getDividends(any(),anyString()); verifyNoInteractions(dividends,persistence);
    }
    @Test void legacyUndatedKisPriceMustBeRefreshedBeforeCallingItEod() {
        var service = service(); var saved = asset(LocalDateTime.now(clock));
        saved.updatePrice(new BigDecimal("85000"),null,LocalDateTime.now(clock));
        when(korean.getQuote(Market.KR,"005930")).thenThrow(ApiException.rateLimit());
        assertThatThrownBy(() -> service.getAsset(Market.KR,"005930")).isInstanceOf(ApiException.class);
        verify(korean).getQuote(Market.KR,"005930");
    }
}
