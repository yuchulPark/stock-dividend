package com.stock.external.opendart;
import java.time.*;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.config.CacheProperties;
import com.stock.entity.AssetSearchCache;
import com.stock.exception.ApiException;
import com.stock.repository.AssetSearchCacheRepository;
import org.springframework.stereotype.Service;
@Service
public class OpenDartCorporationCatalog {
    public static final String CACHE_KEY = "OPENDART:CORPORATIONS:v1";
    private final OpenDartApiClient client;
    private final OpenDartMapper mapper;
    private final ObjectMapper json;
    private final AssetSearchCacheRepository caches;
    private final CacheProperties properties;
    private final Clock clock;
    public OpenDartCorporationCatalog(OpenDartApiClient client, OpenDartMapper mapper, ObjectMapper json,
            AssetSearchCacheRepository caches, CacheProperties properties, Clock clock) {
        this.client = client; this.mapper = mapper; this.json = json; this.caches = caches; this.properties = properties; this.clock = clock;
    }
    public synchronized String corporation(String ticker) {
        var saved = caches.findById(CACHE_KEY).orElse(null);
        Map<String,String> data = null;
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (saved != null && !saved.getSyncedAt().isAfter(now) && saved.getSyncedAt().plus(properties.getAssetTtl()).isAfter(now)) {
            try { data = json.readValue(saved.getPayload(), new TypeReference<Map<String,String>>() {});
                if (data == null || data.isEmpty() || data.entrySet().stream().anyMatch(e -> !e.getKey().matches("[0-9A-Z]{6}") || e.getValue() == null || !e.getValue().matches("[0-9]{8}"))) data = null;
            } catch (Exception ignored) { data = null; }
        }
        if (data == null) {
            data = mapper.corporations(client.corporations());
            try { caches.saveAndFlush(new AssetSearchCache(CACHE_KEY, json.writeValueAsString(data), now)); }
            catch (Exception ex) { throw new IllegalStateException("Cannot persist corporation mapping"); }
        }
        String code = data.get(ticker);
        if (code == null) throw new ApiException("DIVIDEND_DATA_NOT_SUPPORTED", "해당 종목의 OpenDART 기업 고유번호를 확인할 수 없습니다. 우선주 등은 별도 매핑이 필요합니다.", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        return code;
    }
}
