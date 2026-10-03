package com.stock.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import jakarta.persistence.*;

@Entity
@Table(name = "stock_asset", uniqueConstraints = @UniqueConstraint(name = "uk_stock_asset_market_ticker", columnNames = {"market", "ticker"}))
public class StockAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32) private String ticker;
    @Column(nullable = false, length = 200) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Market market;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private AssetType assetType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Currency currency;
    @Column(precision = 24, scale = 8) private BigDecimal currentPrice;
    private LocalDateTime priceUpdatedAt;
    private LocalDate priceAsOfDate;
    private LocalDateTime metadataUpdatedAt;
    private LocalDateTime dividendSyncedAt;
    @Column(nullable = false) private Boolean active = true;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected StockAsset() {}
    public Long getId() { return id; }
    public String getTicker() { return ticker; }
    public String getName() { return name; }
    public Market getMarket() { return market; }
    public AssetType getAssetType() { return assetType; }
    public Currency getCurrency() { return currency; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public LocalDateTime getPriceUpdatedAt() { return priceUpdatedAt; }
    public LocalDate getPriceAsOfDate() { return priceAsOfDate; }
    public LocalDateTime getMetadataUpdatedAt() { return metadataUpdatedAt; }
    public LocalDateTime getDividendSyncedAt() { return dividendSyncedAt; }
    public Boolean getActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public StockAsset(String ticker, String name, Market market, AssetType assetType, Currency currency) {
        this.ticker = ticker; this.name = name; this.market = market;
        this.assetType = assetType; this.currency = currency;
    }
    public void updateMetadata(String name, AssetType type, Currency currency, LocalDateTime now) {
        this.name = name; this.assetType = type; this.currency = currency; this.metadataUpdatedAt = now;
    }
    public void updatePrice(BigDecimal price, LocalDate asOf, LocalDateTime now) {
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("Price must be positive");
        currentPrice = price; priceAsOfDate = asOf; priceUpdatedAt = now;
    }
    public void markDividendsSynced(LocalDateTime now) { dividendSyncedAt = now; }
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(ZoneOffset.UTC); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
}
