package com.stock.external.krx;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.config.*;
import com.stock.entity.AssetSearchCache;
import com.stock.entity.AssetType;
import com.stock.entity.Currency;
import com.stock.entity.Market;
import com.stock.exception.ApiException;
import com.stock.external.krx.dto.KrxResponse;
import com.stock.external.market.KrxMarketDataProvider;
import com.stock.repository.AssetSearchCacheRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KrxTests {
    private final KrxMapper mapper = new KrxMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final KrxApiClient client = mock(KrxApiClient.class);
    private final AssetSearchCacheRepository caches = mock(AssetSearchCacheRepository.class);
    private final KrxProperties properties = new KrxProperties();
    private KrxCatalog catalog() { return new KrxCatalog(client, mapper, properties, new CacheProperties(), caches, json, clock); }
    private KrxResponse.Row info(String ticker, String market) {
        return new KrxResponse.Row("KR7005930003", ticker, "삼성전자", market, "보통주", null, null);
    }
    private KrxResponse.Row price(String ticker, String date, String amount) {
        return new KrxResponse.Row(ticker, null, "공식 종목", null, null, date, amount);
    }
    @Test void identifiesKospiKosdaqAndEtfOnlyFromOfficialSource() {
        var kospi = mapper.instrument(info("005930", "KOSPI"), KrxApiClient.Api.KOSPI_INFO);
        var kosdaq = mapper.instrument(info("035900", "KOSDAQ"), KrxApiClient.Api.KOSDAQ_INFO);
        var etf = mapper.instrument(price("490590", "20261002", "10000"), KrxApiClient.Api.ETF_PRICE);
        assertThat(kospi.asset().ticker()).isEqualTo("005930");
        assertThat(kospi.exchange()).isEqualTo("KOSPI"); assertThat(kosdaq.exchange()).isEqualTo("KOSDAQ");
        assertThat(kospi.asset().assetType()).isEqualTo(AssetType.STOCK);
        assertThat(etf.asset().assetType()).isEqualTo(AssetType.ETF);
        assertThat(etf.asset().currency()).isEqualTo(Currency.KRW);
        assertThat(kospi.asset().market()).isEqualTo(Market.KR);
        assertThatThrownBy(() -> mapper.instrument(info("005930", "KOSDAQ"), KrxApiClient.Api.KOSPI_INFO)).isInstanceOf(ApiException.class);
    }
    @Test void mapsExactCloseAndTradingDateAndRejectsBadValues() {
        var date = LocalDate.of(2026, 10, 2);
        var quote = mapper.quote(price("005930", "20261002", "85,000.12345678"), "005930", date);
        assertThat(quote.price()).isEqualByComparingTo("85000.12345678"); assertThat(quote.asOfDate()).isEqualTo(date);
        for (String amount : List.of("0", "-100", "NaN", "1,00", "1.123456789"))
            assertThatThrownBy(() -> mapper.quote(price("005930", "20261002", amount), "005930", date)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> mapper.quote(price("005930", "20261001", "85000"), "005930", date)).isInstanceOf(ApiException.class);
    }
    @Test void clientUsesOfficialEndpointHeaderAndDateWithoutSearchParameter() {
        var builder = RestClient.builder().baseUrl("https://data-dbg.krx.co.kr");
        var server = MockRestServiceServer.bindTo(builder).build(); properties.setAuthKey("mock-key");
        server.expect(requestTo("https://data-dbg.krx.co.kr/svc/apis/sto/stk_bydd_trd?basDd=20261002"))
                .andExpect(header("AUTH_KEY", "mock-key"))
                .andRespond(withSuccess("{\"OutBlock_1\":[{\"ISU_CD\":\"005930\",\"BAS_DD\":\"20261002\",\"TDD_CLSPRC\":\"85000\"}]}", MediaType.APPLICATION_JSON));
        var api = new KrxApiClient(builder.build(), json, properties);
        assertThat(api.get(KrxApiClient.Api.KOSPI_PRICE, LocalDate.of(2026,10,2)).rows()).hasSize(1); server.verify();
    }
    @Test void missingKeyFailsOnlyOnCallAndTransportErrorDoesNotExposeKey() {
        var builder = RestClient.builder().baseUrl("https://data-dbg.krx.co.kr");
        var server = MockRestServiceServer.bindTo(builder).build();
        var api = new KrxApiClient(builder.build(), json, properties);
        assertThatThrownBy(() -> api.get(KrxApiClient.Api.ETF_PRICE, LocalDate.of(2026,10,2)))
                .extracting("code").isEqualTo("EXTERNAL_API_KEY_MISSING");
        properties.setAuthKey("mock-secret");
        server.expect(anything()).andRespond(withServerError());
        assertThatThrownBy(() -> api.get(KrxApiClient.Api.ETF_PRICE, LocalDate.of(2026,10,2)))
                .isInstanceOf(ApiException.class).hasMessageNotContaining("mock-secret"); server.verify();
    }
    @Test void cacheMissSkipsWeekendAndEmptyHolidayAndStoresActualTradingDay() {
        var date = LocalDate.of(2026, 10, 2);
        when(client.get(KrxApiClient.Api.KOSPI_PRICE, date)).thenReturn(new KrxResponse.Data(List.of()));
        when(client.get(KrxApiClient.Api.KOSPI_PRICE, date.minusDays(1))).thenReturn(new KrxResponse.Data(List.of(price("005930", "20261001", "85000"))));
        var result = catalog().latest(KrxApiClient.Api.KOSPI_PRICE, false);
        assertThat(result.date()).isEqualTo(date.minusDays(1));
        verify(client, times(2)).get(any(), any()); verify(caches).saveAndFlush(any());
    }
    @Test void freshSnapshotAvoidsApiAndForceBypassesIt() throws Exception {
        var snapshot = new KrxCatalog.Snapshot(LocalDate.of(2026,10,2), List.of(price("005930", "20261002", "85000")));
        when(caches.findById("KRX:v1:KOSPI_PRICE")).thenReturn(Optional.of(new AssetSearchCache("KRX:v1:KOSPI_PRICE", json.writeValueAsString(snapshot), LocalDateTime.now(clock).minusHours(2))));
        var catalog = catalog(); assertThat(catalog.latest(KrxApiClient.Api.KOSPI_PRICE, false)).isEqualTo(snapshot); verifyNoInteractions(client);
        when(client.get(KrxApiClient.Api.KOSPI_PRICE, snapshot.date())).thenReturn(new KrxResponse.Data(snapshot.rows()));
        catalog.latest(KrxApiClient.Api.KOSPI_PRICE, true); verify(client).get(KrxApiClient.Api.KOSPI_PRICE, snapshot.date());
    }
    @Test void unavailableDatesStopWithinConfiguredLookback() {
        properties.setLookbackDays(4);
        when(client.get(any(), any())).thenReturn(new KrxResponse.Data(List.of()));
        assertThatThrownBy(() -> catalog().latest(KrxApiClient.Api.KOSPI_PRICE, false)).extracting("code").isEqualTo("EXTERNAL_API_DATA_UNAVAILABLE");
        verify(client, times(2)).get(any(), any());
    }
    @Test void staleSnapshotsProvideSearchFallbackWithoutNetwork() throws Exception {
        var snapshot = new KrxCatalog.Snapshot(LocalDate.of(2026,10,2), List.of(info("005930", "KOSPI")));
        when(caches.findById("KRX:v1:KOSPI_INFO")).thenReturn(Optional.of(new AssetSearchCache("KRX:v1:KOSPI_INFO", json.writeValueAsString(snapshot), LocalDateTime.now(clock).minusDays(3))));
        assertThat(catalog().cachedSearch("삼성")).extracting(a -> a.ticker()).containsExactly("005930"); verifyNoInteractions(client);
    }
    @Test void providerSelectsKosdaqAndEtfPriceAndForceRefreshesSnapshot() {
        var catalog = mock(KrxCatalog.class);
        var provider = new KrxMarketDataProvider(catalog, mapper);
        when(catalog.instrument("035900")).thenReturn(mapper.instrument(info("035900", "KOSDAQ"), KrxApiClient.Api.KOSDAQ_INFO));
        when(catalog.latest(KrxApiClient.Api.KOSDAQ_PRICE, false)).thenReturn(new KrxCatalog.Snapshot(LocalDate.of(2026,10,2), List.of(price("035900","20261002","60000"))));
        assertThat(provider.getQuote(Market.KR, "035900").price()).isEqualByComparingTo("60000");
        when(catalog.instrument("490590")).thenReturn(mapper.instrument(price("490590","20261002","10000"), KrxApiClient.Api.ETF_PRICE));
        when(catalog.latest(KrxApiClient.Api.ETF_PRICE, true)).thenReturn(new KrxCatalog.Snapshot(LocalDate.of(2026,10,2), List.of(price("490590","20261002","10000"))));
        provider.refreshQuote(Market.KR,"490590"); verify(catalog).latest(KrxApiClient.Api.ETF_PRICE, true);
    }
}
