package com.stock.external.market;
import java.util.List;
import com.stock.entity.Market;
import com.stock.external.krx.*;
import com.stock.external.model.*;
import org.springframework.stereotype.Component;
@Component
public class KrxMarketDataProvider implements MarketDataProvider {
    private final KrxCatalog catalog;
    private final KrxMapper mapper;
    public KrxMarketDataProvider(KrxCatalog catalog, KrxMapper mapper) { this.catalog = catalog; this.mapper = mapper; }
    public boolean supports(Market market) { return market == Market.KR; }
    public String searchCacheKey(Market market, String term) { return "KR:KRX:v1:" + term; }
    public List<AssetData> searchAssets(Market market, String term) { require(market); return catalog.search(term, false); }
    public List<AssetData> refreshSearchAssets(Market market, String term) { require(market); return catalog.search(term, true); }
    public List<AssetData> cachedSearchAssets(Market market, String term) { require(market); return catalog.cachedSearch(term); }
    public QuoteData getQuote(Market market, String ticker) {
        return quote(market, ticker, false);
    }
    public QuoteData refreshQuote(Market market, String ticker) { return quote(market, ticker, true); }
    private QuoteData quote(Market market, String ticker, boolean force) {
        require(market);
        var instrument = catalog.instrument(ticker);
        var api = switch (instrument.exchange()) {
            case "KOSPI" -> KrxApiClient.Api.KOSPI_PRICE;
            case "KOSDAQ" -> KrxApiClient.Api.KOSDAQ_PRICE;
            default -> KrxApiClient.Api.ETF_PRICE;
        };
        var data = catalog.latest(api, force);
        var row = data.rows().stream().filter(r -> ticker.equals(r.code())).findFirst().orElseThrow(com.stock.exception.ApiException::notFound);
        return mapper.quote(row, ticker, data.date());
    }
    private void require(Market market) { if (!supports(market)) throw new IllegalArgumentException("KR only"); }
}
