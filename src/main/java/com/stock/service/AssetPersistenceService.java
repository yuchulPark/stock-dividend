package com.stock.service;
import java.time.*;
import java.util.List;
import com.stock.entity.*;
import com.stock.external.model.*;
import com.stock.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AssetPersistenceService {
    private final StockAssetRepository assets;
    private final DividendRepository dividends;
    private final Clock clock;
    public AssetPersistenceService(StockAssetRepository assets, DividendRepository dividends, Clock clock) {
        this.assets = assets; this.dividends = dividends; this.clock = clock;
    }
    @Transactional
    public StockAsset metadata(AssetData data) {
        StockAsset asset = assets.findByMarketAndTicker(data.market(), data.ticker()).orElseGet(() ->
                new StockAsset(data.ticker(), data.name(), data.market(), data.assetType(), data.currency()));
        asset.updateMetadata(data.name(), data.assetType(), data.currency(), now());
        return assets.saveAndFlush(asset);
    }
    @Transactional
    public StockAsset price(Long assetId, QuoteData data) {
        StockAsset asset = assets.findById(assetId).orElseThrow();
        asset.updatePrice(data.price(), data.asOfDate(), now());
        return assets.saveAndFlush(asset);
    }
    @Transactional
    public void dividends(Long assetId, DataSourceType source, List<DividendData> data) {
        StockAsset asset = assets.findById(assetId).orElseThrow();
        // Full response is validated by Provider before opening this transaction.
        for (DividendData row : data) {
            if (row.businessYear() != null) {
                var existing = dividends.findByStockAssetIdAndSourceAndBusinessYearAndReportCodeAndShareClass(
                        assetId, source, row.businessYear(), row.reportCode(), row.shareClass());
                // A corrected zero-dividend report removes an earlier positive amount; zero is never stored.
                if (row.amount().signum() == 0) { existing.ifPresent(dividends::delete); continue; }
                Dividend report = existing.orElseGet(() -> new Dividend(asset, null, source));
                report.report(row.businessYear(), row.reportCode(), row.filingNumber(), row.shareClass());
                report.update(row.amount(), null, null, row.currency());
                dividends.save(report);
                continue;
            }
            if (row.exDate() == null || source == DataSourceType.OPEN_DART)
                throw new IllegalArgumentException("Event dividend requires a real ex-dividend date");
            Dividend dividend = dividends.findByStockAssetIdAndSourceAndExDividendDate(assetId, source, row.exDate())
                    .orElseGet(() -> new Dividend(asset, row.exDate(), source));
            dividend.update(row.amount(), row.recordDate(), row.paymentDate(), row.currency());
            dividends.save(dividend);
        }
        dividends.flush();
        asset.markDividendsSynced(now()); // A successful empty history is cached too.
        assets.saveAndFlush(asset);
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
