package com.stock.external.krx;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.config.*;
import com.stock.entity.AssetSearchCache;
import com.stock.exception.ApiException;
import com.stock.external.OfficialApiErrors;
import com.stock.external.krx.dto.KrxResponse;
import com.stock.external.model.AssetData;
import com.stock.repository.AssetSearchCacheRepository;
import org.springframework.stereotype.Service;
@Service
public class KrxCatalog {
    public record Snapshot(LocalDate date, List<KrxResponse.Row> rows) {}
    private final KrxApiClient client;
    private final KrxMapper mapper;
    private final KrxProperties properties;
    private final CacheProperties ttl;
    private final AssetSearchCacheRepository caches;
    private final ObjectMapper json;
    private final Clock clock;
    public KrxCatalog(KrxApiClient client, KrxMapper mapper, KrxProperties properties, CacheProperties ttl,
            AssetSearchCacheRepository caches, ObjectMapper json, Clock clock) {
        this.client = client; this.mapper = mapper; this.properties = properties; this.ttl = ttl;
        this.caches = caches; this.json = json; this.clock = clock;
    }
    public synchronized Snapshot latest(KrxApiClient.Api api, boolean force) {
        String key = "KRX:v1:" + api;
        var saved = caches.findById(key).orElse(null);
        Duration duration = api == KrxApiClient.Api.KOSPI_INFO || api == KrxApiClient.Api.KOSDAQ_INFO ? ttl.getAssetTtl() : ttl.getKrPriceTtl();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        if (!force && saved != null && saved.getSyncedAt() != null && !saved.getSyncedAt().isAfter(now())
                && saved.getSyncedAt().plus(duration).isAfter(now())) {
            try {
                Snapshot data = json.readValue(saved.getPayload(), Snapshot.class);
                if (data.date() != null && data.date().isBefore(today) && data.rows() != null && !data.rows().isEmpty()) return data;
            } catch (Exception ignored) { /* Refresh damaged cache. */ }
        }
        for (int day = 1; day <= properties.getLookbackDays(); day++) {
            LocalDate date = today.minusDays(day);
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            var response = client.get(api, date);
            if (response.rows().isEmpty()) continue;
            // Validate the full snapshot before storing it; fundamental rows do not contain BAS_DD.
            Set<String> codes = new HashSet<>();
            for (var row : response.rows()) {
                if (row == null) throw ApiException.invalidResponse();
                String code;
                if (api == KrxApiClient.Api.KOSPI_INFO || api == KrxApiClient.Api.KOSDAQ_INFO)
                    code = mapper.instrument(row, api).asset().ticker();
                else {
                    code = row.code();
                    if (code == null || !code.matches("[0-9A-Z]{6}") || !date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE).equals(row.date()))
                        throw ApiException.invalidResponse();
                    if (api == KrxApiClient.Api.ETF_PRICE) mapper.instrument(row, api);
                }
                if (!codes.add(code)) throw ApiException.invalidResponse();
            }
            Snapshot snapshot = new Snapshot(date, List.copyOf(response.rows()));
            try { caches.saveAndFlush(new AssetSearchCache(key, json.writeValueAsString(snapshot), now())); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize KRX cache"); }
            return snapshot;
        }
        throw OfficialApiErrors.error("EXTERNAL_API_DATA_UNAVAILABLE", "조회 범위에 이용 가능한 KRX 거래일 데이터가 없습니다.");
    }
    public synchronized List<AssetData> search(String term, boolean force) {
        List<AssetData> result = new ArrayList<>();
        for (var api : List.of(KrxApiClient.Api.KOSPI_INFO, KrxApiClient.Api.KOSDAQ_INFO, KrxApiClient.Api.ETF_PRICE))
            latest(api, force).rows().stream().map(r -> mapper.instrument(r, api).asset()).filter(a -> matches(a, term)).forEach(result::add);
        return result.stream().sorted(Comparator.comparing((AssetData a) -> !a.ticker().equals(term)).thenComparing(AssetData::ticker)).limit(100).toList();
    }
    public synchronized KrxMapper.Instrument instrument(String ticker) {
        for (var api : List.of(KrxApiClient.Api.KOSPI_INFO, KrxApiClient.Api.KOSDAQ_INFO, KrxApiClient.Api.ETF_PRICE)) {
            for (var row : latest(api, false).rows()) {
                var instrument = mapper.instrument(row, api);
                if (ticker.equals(instrument.asset().ticker())) return instrument;
            }
        }
        throw ApiException.notFound();
    }
    public List<AssetData> cachedSearch(String term) {
        List<AssetData> result = new ArrayList<>();
        for (var api : List.of(KrxApiClient.Api.KOSPI_INFO, KrxApiClient.Api.KOSDAQ_INFO, KrxApiClient.Api.ETF_PRICE)) {
            var saved = caches.findById("KRX:v1:" + api).orElse(null);
            if (saved == null) continue;
            try { json.readValue(saved.getPayload(), Snapshot.class).rows().stream().map(r -> mapper.instrument(r, api).asset())
                    .filter(a -> matches(a, term)).forEach(result::add); }
            catch (Exception ignored) { /* Ignore unusable fallback entries. */ }
        }
        return result.stream().distinct().limit(100).toList();
    }
    private boolean matches(AssetData data, String term) { return data.ticker().contains(term.toUpperCase(Locale.ROOT)) || data.name().toUpperCase(Locale.ROOT).contains(term.toUpperCase(Locale.ROOT)); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
