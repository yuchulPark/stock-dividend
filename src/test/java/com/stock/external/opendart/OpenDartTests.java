package com.stock.external.opendart;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.config.*;
import com.stock.entity.AssetSearchCache;
import com.stock.entity.AssetType;
import com.stock.entity.Currency;
import com.stock.entity.Market;
import com.stock.exception.ApiException;
import com.stock.external.dividend.OpenDartDividendProvider;
import com.stock.external.krx.*;
import com.stock.external.model.AssetData;
import com.stock.external.opendart.dto.OpenDartResponse;
import com.stock.repository.AssetSearchCacheRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenDartTests {
    private final OpenDartMapper mapper = new OpenDartMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper json = new ObjectMapper();
    private final OpenDartProperties properties = new OpenDartProperties();
    static byte[] archive(String xml) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("CORPCODE.xml")); zip.write(xml.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    private byte[] corporations() throws IOException {
        return archive("<result><list><corp_code>00126380</corp_code><stock_code>005930</stock_code></list><list><corp_code>00000001</corp_code><stock_code> </stock_code></list></result>");
    }
    private OpenDartResponse.Row row(String se, String kind, String amount) {
        return new OpenDartResponse.Row("20260301000001", "00126380", se, kind, amount, "2025-12-31");
    }
    @Test void zipMapsListedTickerToEightDigitCorporationAndSkipsUnlisted() throws Exception {
        assertThat(mapper.corporations(corporations())).containsExactlyEntriesOf(Map.of("005930", "00126380"));
    }
    @Test void xmlRejectsExternalEntitiesAndConflictingCodes() throws Exception {
        assertThatThrownBy(() -> mapper.corporations(archive("<!DOCTYPE result [<!ENTITY xxe SYSTEM 'file:///missing'>]><result>&xxe;</result>"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> mapper.corporations(archive("<result><list><stock_code>005930</stock_code><corp_code>00126380</corp_code></list><list><stock_code>005930</stock_code><corp_code>11111111</corp_code></list></result>"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> mapper.corporations("<result><status>010</status></result>".getBytes(StandardCharsets.UTF_8))).extracting("code").isEqualTo("EXTERNAL_API_AUTH_FAILED");
    }
    @Test void usesOnlyCurrentPerCommonShareCashAndDoesNotInventDates() {
        var data = new OpenDartResponse.Data("000", "ok", List.of(row("주당 현금배당금(원)", "보통주", "1,444"),
                row("현금배당금총액(백만원)", "보통주", "999999"), row("주당 현금배당금(원)", "우선주", "1445")));
        var result = mapper.dividends(data, "00126380", 2025, "11011");
        assertThat(result).hasSize(1); var dividend = result.get(0);
        assertThat(dividend.amount()).isEqualByComparingTo("1444"); assertThat(dividend.businessYear()).isEqualTo(2025);
        assertThat(dividend.currency()).isEqualTo(Currency.KRW); assertThat(dividend.reportCode()).isEqualTo("11011");
        assertThat(dividend.filingNumber()).isEqualTo("20260301000001");
        assertThat(dividend.exDate()).isNull(); assertThat(dividend.paymentDate()).isNull(); assertThat(dividend.recordDate()).isNull();
    }
    @Test void noDataUnavailableZeroAndApiStatusAreDistinct() {
        assertThat(mapper.dividends(new OpenDartResponse.Data("013", "no data", null), "00126380", 2025, "11011")).isEmpty();
        assertThat(mapper.dividends(new OpenDartResponse.Data("000", "ok", List.of(row("주당 현금배당금(원)", "보통주", "-"))), "00126380", 2025, "11011")).isEmpty();
        assertThat(mapper.dividends(new OpenDartResponse.Data("000", "ok", List.of(row("주당 현금배당금(원)", "보통주", "0"))), "00126380", 2025, "11011").get(0).amount()).isZero();
        assertThatThrownBy(() -> OpenDartMapper.checkStatus("020")).extracting("code").isEqualTo("EXTERNAL_API_RATE_LIMIT");
        assertThatThrownBy(() -> OpenDartMapper.checkStatus("010")).extracting("code").isEqualTo("EXTERNAL_API_AUTH_FAILED");
        assertThatThrownBy(() -> mapper.dividends(new OpenDartResponse.Data("000", "ok", List.of(row("주당 현금배당금(원)", "보통주", "NaN"))), "00126380", 2025, "11011")).isInstanceOf(ApiException.class);
    }
    @Test void clientSendsOfficialCorporationYearAndReportParameters() {
        var builder = RestClient.builder().baseUrl("https://opendart.fss.or.kr");
        var server = MockRestServiceServer.bindTo(builder).build(); properties.setApiKey("mock-key");
        server.expect(requestTo("https://opendart.fss.or.kr/api/alotMatter.json?crtfc_key=mock-key&corp_code=00126380&bsns_year=2025&reprt_code=11011"))
                .andRespond(withSuccess("{\"status\":\"013\",\"message\":\"no data\"}", MediaType.APPLICATION_JSON));
        var client = new OpenDartApiClient(builder.build(), json, properties);
        assertThat(client.dividends("00126380", 2025, "11011").status()).isEqualTo("013"); server.verify();
    }
    @Test void clientDownloadsOfficialZipAndSanitizesErrors() throws Exception {
        var builder = RestClient.builder().baseUrl("https://opendart.fss.or.kr");
        var server = MockRestServiceServer.bindTo(builder).build(); properties.setApiKey("mock-secret");
        server.expect(requestTo("https://opendart.fss.or.kr/api/corpCode.xml?crtfc_key=mock-secret"))
                .andRespond(withSuccess(corporations(), MediaType.APPLICATION_OCTET_STREAM));
        server.expect(anything()).andRespond(withServerError());
        var client = new OpenDartApiClient(builder.build(), json, properties);
        assertThat(mapper.corporations(client.corporations())).containsKey("005930");
        assertThatThrownBy(() -> client.dividends("00126380",2025,"11011")).isInstanceOf(ApiException.class).hasMessageNotContaining("mock-secret"); server.verify();
        properties.setApiKey("");
        assertThatThrownBy(client::corporations).extracting("code").isEqualTo("EXTERNAL_API_KEY_MISSING");
    }
    @Test void corporationCacheMissDownloadsOnceAndFreshHitAvoidsDownload() throws Exception {
        var client = mock(OpenDartApiClient.class); var caches = mock(AssetSearchCacheRepository.class);
        var stored = new java.util.concurrent.atomic.AtomicReference<AssetSearchCache>();
        when(caches.findById(OpenDartCorporationCatalog.CACHE_KEY)).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(caches.saveAndFlush(any())).thenAnswer(call -> { stored.set(call.getArgument(0)); return stored.get(); });
        when(client.corporations()).thenReturn(corporations());
        var catalog = new OpenDartCorporationCatalog(client, mapper, json, caches, new CacheProperties(), clock);
        assertThat(catalog.corporation("005930")).isEqualTo("00126380"); assertThat(catalog.corporation("005930")).isEqualTo("00126380");
        verify(client, times(1)).corporations();
    }
    @Test void providerFetchesConfiguredCompletedYearsAndNeverTreatsTickerAsCorporation() {
        var client = mock(OpenDartApiClient.class); var corporations = mock(OpenDartCorporationCatalog.class); var krx = mock(KrxCatalog.class);
        when(krx.instrument("005930")).thenReturn(new KrxMapper.Instrument(new AssetData("005930","삼성전자",Market.KR,AssetType.STOCK,Currency.KRW),"KOSPI","보통주"));
        when(corporations.corporation("005930")).thenReturn("00126380"); properties.setYears(2);
        when(client.dividends(eq("00126380"), anyInt(), eq("11011"))).thenReturn(new OpenDartResponse.Data("013","no data",null));
        var provider = new OpenDartDividendProvider(corporations,client,mapper,properties,krx,clock);
        assertThat(provider.getDividends(Market.KR,"005930")).isEmpty();
        verify(client).dividends("00126380",2025,"11011"); verify(client).dividends("00126380",2024,"11011"); verifyNoMoreInteractions(client);
        assertThat(provider.supports(Market.KR,AssetType.ETF)).isFalse();
    }
    @Test void preferredStockCannotBeMappedToCommonShareDividend() {
        var client = mock(OpenDartApiClient.class); var corporations = mock(OpenDartCorporationCatalog.class); var krx = mock(KrxCatalog.class);
        when(krx.instrument("005935")).thenReturn(new KrxMapper.Instrument(new AssetData("005935","삼성전자우",Market.KR,AssetType.STOCK,Currency.KRW),"KOSPI","우선주"));
        var provider = new OpenDartDividendProvider(corporations,client,mapper,properties,krx,clock);
        assertThatThrownBy(() -> provider.getDividends(Market.KR,"005935")).extracting("code").isEqualTo("DIVIDEND_DATA_NOT_SUPPORTED");
        verifyNoInteractions(client, corporations);
    }
}
