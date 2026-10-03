package com.stock.external.market;
import java.util.List;
import com.stock.entity.Market;
import com.stock.external.model.*;
public interface MarketDataProvider {
    boolean supports(Market market);
    default String searchCacheKey(Market market, String term) { return market + ":" + term; }
    List<AssetData> searchAssets(Market market, String keyword);
    default List<AssetData> refreshSearchAssets(Market market, String keyword) { return searchAssets(market, keyword); }
    default List<AssetData> cachedSearchAssets(Market market, String keyword) { return List.of(); }
    QuoteData getQuote(Market market, String ticker);
    default QuoteData refreshQuote(Market market, String ticker) { return getQuote(market, ticker); }
}
