package com.stock.config;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("stock.cache")
public class CacheProperties {
    private Duration priceTtl = Duration.ofMinutes(30);
    private Duration krPriceTtl = Duration.ofHours(6);
    public Duration getKrPriceTtl() { return krPriceTtl; }
    public void setKrPriceTtl(Duration ttl) { krPriceTtl = ttl; }
    private Duration assetTtl = Duration.ofHours(24);
    private Duration dividendTtl = Duration.ofHours(24);
    public Duration getPriceTtl() { return priceTtl; }
    public void setPriceTtl(Duration priceTtl) { this.priceTtl = priceTtl; }
    public Duration getAssetTtl() { return assetTtl; }
    public void setAssetTtl(Duration assetTtl) { this.assetTtl = assetTtl; }
    public Duration getDividendTtl() { return dividendTtl; }
    public void setDividendTtl(Duration dividendTtl) { this.dividendTtl = dividendTtl; }
    @jakarta.annotation.PostConstruct void validate() {
        if (priceTtl == null || krPriceTtl == null || assetTtl == null || dividendTtl == null
                || priceTtl.isNegative() || krPriceTtl.isNegative() || assetTtl.isNegative() || dividendTtl.isNegative())
            throw new IllegalArgumentException("Cache TTLs must be non-negative");
    }
}
