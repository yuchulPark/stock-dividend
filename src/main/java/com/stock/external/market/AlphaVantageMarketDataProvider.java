package com.stock.external.market;
import java.util.List;
import com.stock.entity.Market;
import com.stock.external.alphavantage.*;
import com.stock.external.alphavantage.dto.AlphaVantageResponse;
import com.stock.external.model.*;
import org.springframework.stereotype.Component;
@Component
public class AlphaVantageMarketDataProvider implements MarketDataProvider {
    private final AlphaVantageClient client;
    private final AlphaVantageMapper mapper;
    public AlphaVantageMarketDataProvider(AlphaVantageClient client, AlphaVantageMapper mapper) { this.client = client; this.mapper = mapper; }
    public boolean supports(Market market) { return market == Market.US; }
    public List<AssetData> searchAssets(Market market, String keyword) {
        requireUS(market); return client.get("SYMBOL_SEARCH", "keywords", keyword, AlphaVantageResponse.Search.class, mapper::search);
    }
    public QuoteData getQuote(Market market, String ticker) {
        requireUS(market); return client.get("GLOBAL_QUOTE", "symbol", ticker, AlphaVantageResponse.Quote.class, data -> mapper.quote(data, ticker));
    }
    private static void requireUS(Market market) {
        if (market != Market.US) throw new IllegalArgumentException("US only");
    }
}
