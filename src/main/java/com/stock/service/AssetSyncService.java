package com.stock.service;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.config.CacheProperties;
import com.stock.dto.response.*;
import com.stock.entity.AssetSearchCache;
import com.stock.entity.Market;
import com.stock.entity.StockAsset;
import com.stock.exception.ApiException;
import com.stock.external.market.MarketDataProvider;
import com.stock.external.market.MarketDataProviderRegistry;
import com.stock.external.dividend.DividendDataProvider;
import com.stock.external.model.AssetData;
import com.stock.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AssetSyncService {
    private final StockAssetRepository assets;
    private final DividendRepository dividends;
    private final AssetSearchCacheRepository searchCache;
    private final MarketDataProviderRegistry providers;
    private final AssetPersistenceService persistence;
    private final RefreshCoordinator coordinator;
    private final CacheProperties cache;
    private final ObjectMapper mapper;
    private final Clock clock;
    public AssetSyncService(StockAssetRepository assets, DividendRepository dividends,
            AssetSearchCacheRepository searchCache, MarketDataProviderRegistry providers, AssetPersistenceService persistence,
            RefreshCoordinator coordinator, CacheProperties cache, ObjectMapper mapper, Clock clock) {
        this.assets = assets; this.dividends = dividends; this.searchCache = searchCache;
        this.providers = providers; this.persistence = persistence;
        this.coordinator = coordinator; this.cache = cache; this.mapper = mapper; this.clock = clock;
    }
    public CacheResult<List<AssetSearchResponse>> search(Market market, String keyword) {
        return search(market, keyword, false);
    }
    private CacheResult<List<AssetSearchResponse>> search(Market market, String keyword, boolean force) {
        MarketDataProvider provider = providers.market(market);
        String term = keyword == null ? "" : keyword.strip();
        if (term.isEmpty() || term.length() > 100) throw new IllegalArgumentException("Invalid keyword");
        String providerKey = provider.searchCacheKey(market, term.toUpperCase(Locale.ROOT));
        String key = providerKey != null ? providerKey : market + ":" + term.toUpperCase(Locale.ROOT);
        return coordinator.withLock("search:" + key, () -> {
            AssetSearchCache saved = searchCache.findById(key).orElse(null);
            List<AssetData> stored = readSearch(saved);
            if (!force && stored != null && fresh(saved.getSyncedAt(), cache.getAssetTtl())) return searchResult(stored, true, saved.getSyncedAt(), null);
            try {
                List<AssetData> data = force ? provider.refreshSearchAssets(market, term) : provider.searchAssets(market, term);
                String payload;
                try { payload = mapper.writeValueAsString(data); }
                catch (Exception ex) { throw new IllegalStateException("Cannot serialize search cache"); }
                LocalDateTime now = now();
                searchCache.saveAndFlush(new AssetSearchCache(key, payload, now));
                return searchResult(data, true, now, null);
            } catch (ApiException ex) {
                if (stored != null) return searchResult(stored, false, saved.getSyncedAt(), ex.getCode());
                List<AssetData> local = assets.search(market, term).stream().map(this::assetData).toList();
                if (!local.isEmpty()) return searchResult(local, false, null, ex.getCode());
                List<AssetData> master = provider.cachedSearchAssets(market, term);
                if (!master.isEmpty()) return searchResult(master, false, null, ex.getCode());
                throw ex;
            }
        });
    }
    public StockAssetResponse getAsset(Market market, String rawTicker) {
        String ticker = ticker(market, rawTicker);
        providers.market(market);
        return coordinator.withLock(market + ":" + ticker, () -> asset(market, ticker, false));
    }
    public CacheResult<List<DividendResponse>> getDividends(Market market, String rawTicker) {
        String ticker = ticker(market, rawTicker);
        providers.requireDividends(market);
        return coordinator.withLock(market + ":" + ticker, () -> dividendHistory(market, ticker, false));
    }
    public RefreshResponse refresh(Market market, String rawTicker) {
        String ticker = ticker(market, rawTicker);
        providers.market(market);
        return coordinator.withLock(market + ":" + ticker, () -> {
            StockAssetResponse asset = asset(market, ticker, true);
            if (providers.dividends(market).isEmpty())
                return new RefreshResponse(asset, List.of(), false, "DIVIDEND_PROVIDER_NOT_IMPLEMENTED");
            if (providers.dividends(market, asset.assetType()).isEmpty())
                return new RefreshResponse(asset, List.of(), false, "DIVIDEND_DATA_NOT_SUPPORTED");
            CacheResult<List<DividendResponse>> history = dividendHistory(market, ticker, true);
            return new RefreshResponse(asset, history.data(), history.dataFresh(), history.warningCode());
        });
    }
    private StockAssetResponse asset(Market market, String ticker, boolean force) {
        StockAsset saved = assets.findByMarketAndTicker(market, ticker).orElse(null);
        boolean metadataFresh = saved != null && fresh(saved.getMetadataUpdatedAt(), cache.getAssetTtl());
        String warning = null;
        if (force || !metadataFresh) {
            try { saved = resolveMetadata(market, ticker, force); metadataFresh = true; }
            catch (ApiException ex) {
                if (saved == null) throw ex;
                warning = ex.getCode(); metadataFresh = false;
            }
        }
        boolean priceFresh = saved.getCurrentPrice() != null && (market != Market.KR || saved.getPriceAsOfDate() != null)
                && fresh(saved.getPriceUpdatedAt(), market == Market.KR ? cache.getKrPriceTtl() : cache.getPriceTtl());
        if (force || !priceFresh) {
            try {
                MarketDataProvider provider = providers.market(market);
                saved = persistence.price(saved.getId(), force ? provider.refreshQuote(market, ticker) : provider.getQuote(market, ticker));
                priceFresh = true;
            } catch (ApiException ex) {
                if (saved.getCurrentPrice() == null || (market == Market.KR && saved.getPriceAsOfDate() == null)) throw ex;
                warning = ex.getCode(); priceFresh = false;
            }
        }
        return StockAssetResponse.from(saved, metadataFresh && priceFresh, warning);
    }
    private CacheResult<List<DividendResponse>> dividendHistory(Market market, String ticker, boolean force) {
        StockAsset asset = assets.findByMarketAndTicker(market, ticker).orElse(null);
        if (asset == null) asset = resolveMetadata(market, ticker);
        DividendDataProvider provider = providers.requireDividends(market, asset.getAssetType());
        boolean synced = asset.getDividendSyncedAt() != null;
        boolean isFresh = fresh(asset.getDividendSyncedAt(), cache.getDividendTtl());
        String warning = null;
        if (force || !isFresh) {
            try {
                persistence.dividends(asset.getId(), provider.source(), provider.getDividends(market, ticker));
                asset = assets.findById(asset.getId()).orElseThrow(); isFresh = true;
            } catch (ApiException ex) {
                if (!synced && dividends.findByStockAssetIdOrderByExDividendDateDesc(asset.getId()).isEmpty()) throw ex;
                isFresh = false; warning = ex.getCode();
            }
        }
        List<DividendResponse> result = dividends.findByStockAssetIdOrderByExDividendDateDesc(asset.getId())
                .stream().map(DividendResponse::from).toList();
        return new CacheResult<>(result, isFresh, asset.getDividendSyncedAt(), warning);
    }
    private StockAsset resolveMetadata(Market market, String ticker) {
        return resolveMetadata(market, ticker, false);
    }
    private StockAsset resolveMetadata(Market market, String ticker, boolean force) {
        // Providers resolve metadata from SYMBOL_SEARCH (US) or official KRX snapshots (KR).
        CacheResult<List<AssetSearchResponse>> matches = search(market, ticker, force);
        AssetSearchResponse match = matches.data().stream().filter(a -> ticker.equals(a.ticker())).findFirst().orElse(null);
        if (match == null) {
            if (!matches.dataFresh()) throw new ApiException("EXTERNAL_API_UNAVAILABLE", "종목 기본정보를 확인할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
            throw ApiException.notFound();
        }
        if (!matches.dataFresh()) throw new ApiException("EXTERNAL_API_UNAVAILABLE", "최신 종목 기본정보를 확인할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        return persistence.metadata(new AssetData(match.ticker(), match.name(), match.market(), match.assetType(), match.currency()));
    }
    private static String ticker(Market market, String raw) {
        if (raw == null) throw new IllegalArgumentException("Invalid ticker");
        String value = raw.strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9][A-Z0-9.\\-]{0,31}")) throw new IllegalArgumentException("Invalid ticker");
        // KRX preferred shares can include letters (e.g. 00088K). Preserve leading zeros.
        if (market == Market.KR && !value.matches("[0-9A-Z]{6}")) throw new IllegalArgumentException("Invalid Korean ticker");
        return value;
    }
    private boolean fresh(LocalDateTime time, Duration ttl) {
        return time != null && !time.isAfter(now()) && time.plus(ttl).isAfter(now());
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private AssetData assetData(StockAsset a) { return new AssetData(a.getTicker(), a.getName(), a.getMarket(), a.getAssetType(), a.getCurrency()); }
    private List<AssetData> readSearch(AssetSearchCache saved) {
        if (saved == null) return null;
        try { return mapper.readValue(saved.getPayload(), new TypeReference<List<AssetData>>() {}); }
        catch (Exception ex) { return null; }
    }
    private CacheResult<List<AssetSearchResponse>> searchResult(List<AssetData> data, boolean fresh, LocalDateTime syncedAt, String warning) {
        return new CacheResult<>(data.stream().map(AssetSearchResponse::from).toList(), fresh, syncedAt, warning);
    }
}
