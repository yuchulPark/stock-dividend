package com.stock.dto.response;
import com.stock.entity.*;
import com.stock.external.model.AssetData;
public record AssetSearchResponse(String ticker, String name, Market market, AssetType assetType, Currency currency) {
    public static AssetSearchResponse from(AssetData data) {
        return new AssetSearchResponse(data.ticker(), data.name(), data.market(), data.assetType(), data.currency());
    }
}
