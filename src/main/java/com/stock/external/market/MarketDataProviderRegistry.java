package com.stock.external.market;

import java.util.List;
import java.util.Optional;
import com.stock.entity.Market;
import com.stock.entity.AssetType;
import com.stock.exception.ApiException;
import com.stock.external.dividend.DividendDataProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MarketDataProviderRegistry {
    private final List<MarketDataProvider> markets;
    private final List<DividendDataProvider> dividends;
    public MarketDataProviderRegistry(List<MarketDataProvider> markets, List<DividendDataProvider> dividends) {
        this.markets = List.copyOf(markets); this.dividends = List.copyOf(dividends);
        for (Market market : Market.values()) {
            if (markets.stream().filter(p -> p.supports(market)).count() > 1
                    || dividends.stream().filter(p -> p.supports(market)).count() > 1)
                throw new IllegalStateException("Duplicate provider for " + market);
        }
    }
    public MarketDataProvider market(Market market) {
        return markets.stream().filter(p -> p.supports(market)).findFirst().orElseThrow(() ->
                new ApiException("MARKET_NOT_SUPPORTED", "해당 시장의 금융 데이터 Provider가 구현되지 않았습니다.", HttpStatus.UNPROCESSABLE_ENTITY));
    }
    public Optional<DividendDataProvider> dividends(Market market) {
        return dividends.stream().filter(p -> p.supports(market)).findFirst();
    }
    public DividendDataProvider requireDividends(Market market) {
        return dividends(market).orElseThrow(() -> new ApiException("DIVIDEND_PROVIDER_NOT_IMPLEMENTED",
                "해당 시장의 배당 데이터 Provider가 아직 구현되지 않았습니다.", HttpStatus.NOT_IMPLEMENTED));
    }
    public Optional<DividendDataProvider> dividends(Market market, AssetType type) {
        return dividends(market).filter(p -> p.supports(market, type));
    }
    public DividendDataProvider requireDividends(Market market, AssetType type) {
        requireDividends(market);
        return dividends(market, type).orElseThrow(() -> new ApiException("DIVIDEND_DATA_NOT_SUPPORTED",
                "국내 ETF 분배금은 현재 연결된 공식 API에서 지원하지 않습니다.", HttpStatus.UNPROCESSABLE_ENTITY));
    }
}
