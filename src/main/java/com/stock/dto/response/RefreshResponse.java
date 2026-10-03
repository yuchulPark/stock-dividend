package com.stock.dto.response;
import java.util.List;
public record RefreshResponse(StockAssetResponse asset, List<DividendResponse> dividends,
        boolean dividendDataFresh, String dividendWarningCode) {}
