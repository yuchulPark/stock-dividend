package com.stock.external.model;
import com.stock.entity.*;
public record AssetData(String ticker, String name, Market market, AssetType assetType, Currency currency) {}
