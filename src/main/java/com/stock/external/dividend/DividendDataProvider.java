package com.stock.external.dividend;
import java.util.List;
import com.stock.entity.*;
import com.stock.external.model.DividendData;
public interface DividendDataProvider {
    boolean supports(Market market);
    default boolean supports(Market market, AssetType type) { return supports(market); }
    DataSourceType source();
    List<DividendData> getDividends(Market market, String ticker);
}
