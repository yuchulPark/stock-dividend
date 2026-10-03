package com.stock.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "asset_search_cache")
public class AssetSearchCache {
    @Id @Column(length = 150) private String cacheKey;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(nullable = false) private LocalDateTime syncedAt;
    protected AssetSearchCache() {}
    public String getCacheKey() { return cacheKey; }
    public String getPayload() { return payload; }
    public LocalDateTime getSyncedAt() { return syncedAt; }
    public AssetSearchCache(String key, String payload, LocalDateTime now) {
        cacheKey = key; this.payload = payload; syncedAt = now;
    }
}
