package com.stock.external.dividend;
import java.time.*;
import java.util.*;
import com.stock.config.OpenDartProperties;
import com.stock.entity.*;
import com.stock.exception.ApiException;
import com.stock.external.krx.KrxCatalog;
import com.stock.external.model.DividendData;
import com.stock.external.opendart.*;
import org.springframework.stereotype.Component;
@Component
public class OpenDartDividendProvider implements DividendDataProvider {
    private final OpenDartCorporationCatalog corporations;
    private final OpenDartApiClient client;
    private final OpenDartMapper mapper;
    private final OpenDartProperties properties;
    private final KrxCatalog krx;
    private final Clock clock;
    public OpenDartDividendProvider(OpenDartCorporationCatalog corporations, OpenDartApiClient client, OpenDartMapper mapper,
            OpenDartProperties properties, KrxCatalog krx, Clock clock) {
        this.corporations = corporations; this.client = client; this.mapper = mapper; this.properties = properties; this.krx = krx; this.clock = clock;
    }
    public boolean supports(Market market) { return market == Market.KR; }
    public boolean supports(Market market, AssetType type) { return supports(market) && type == AssetType.STOCK; }
    public DataSourceType source() { return DataSourceType.OPEN_DART; }
    public List<DividendData> getDividends(Market market, String ticker) {
        if (!supports(market)) throw new IllegalArgumentException("KR only");
        var instrument = krx.instrument(ticker);
        if (instrument.asset().assetType() != AssetType.STOCK || !"보통주".equals(instrument.shareClass()))
            throw new ApiException("DIVIDEND_DATA_NOT_SUPPORTED", "국내 ETF 분배금과 우선주 배당 데이터 연동은 현재 지원하지 않습니다.", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        String corporation = corporations.corporation(ticker);
        int lastYear = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))).getYear() - 1;
        List<DividendData> result = new ArrayList<>();
        for (int year = lastYear; year >= Math.max(2015, lastYear - properties.getYears() + 1); year--)
            result.addAll(mapper.dividends(client.dividends(corporation, year, properties.getReportCode()), corporation, year, properties.getReportCode()));
        return List.copyOf(result);
    }
}
