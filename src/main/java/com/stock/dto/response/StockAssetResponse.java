package com.stock.dto.response;
import java.math.BigDecimal;
import java.time.*;
import com.stock.entity.*;
public record StockAssetResponse(Long id, String ticker, String name, Market market, AssetType assetType,
        Currency currency, BigDecimal currentPrice, LocalDateTime priceUpdatedAt, LocalDate priceAsOfDate,
        boolean dataFresh, String warningCode, String priceType) {
    public StockAssetResponse(Long id, String ticker, String name, Market market, AssetType type, Currency currency,
            BigDecimal price, LocalDateTime updated, LocalDate asOf, boolean fresh, String warning) {
        this(id, ticker, name, market, type, currency, price, updated, asOf, fresh, warning,
                market == Market.KR ? "EOD" : "PROVIDER_QUOTE");
    }
    public static StockAssetResponse from(StockAsset asset, boolean fresh, String warning) {
        return new StockAssetResponse(asset.getId(), asset.getTicker(), asset.getName(), asset.getMarket(),
                asset.getAssetType(), asset.getCurrency(), asset.getCurrentPrice(), asset.getPriceUpdatedAt(),
                asset.getPriceAsOfDate(), fresh, warning);
    }
}
