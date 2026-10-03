package com.stock.external.dividend;
import java.util.List;
import com.stock.entity.*;
import com.stock.external.alphavantage.*;
import com.stock.external.alphavantage.dto.AlphaVantageResponse;
import com.stock.external.model.DividendData;
import org.springframework.stereotype.Component;
@Component
public class AlphaVantageDividendProvider implements DividendDataProvider {
    private final AlphaVantageClient client;
    private final AlphaVantageMapper mapper;
    public AlphaVantageDividendProvider(AlphaVantageClient client, AlphaVantageMapper mapper) { this.client = client; this.mapper = mapper; }
    public boolean supports(Market market) { return market == Market.US; }
    public DataSourceType source() { return DataSourceType.ALPHA_VANTAGE; }
    public List<DividendData> getDividends(Market market, String ticker) {
        if (!supports(market)) throw new IllegalArgumentException("US only");
        return client.get("DIVIDENDS", "symbol", ticker, AlphaVantageResponse.Dividends.class, data -> mapper.dividends(data, ticker));
    }
}
