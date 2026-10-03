package com.stock.controller;
import java.util.List;
import com.stock.dto.response.*;
import com.stock.entity.Market;
import com.stock.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/assets")
public class StockAssetController {
    private final AssetSyncService service;
    public StockAssetController(AssetSyncService service) { this.service = service; }
    @GetMapping("/search")
    public ResponseEntity<List<AssetSearchResponse>> search(@RequestParam(defaultValue = "US") Market market, @RequestParam String keyword) {
        return cached(service.search(market, keyword));
    }
    @GetMapping("/{market}/{ticker}")
    public StockAssetResponse detail(@PathVariable Market market, @PathVariable String ticker) {
        return service.getAsset(market, ticker);
    }
    @GetMapping("/{market}/{ticker}/dividends")
    public ResponseEntity<List<DividendResponse>> dividends(@PathVariable Market market, @PathVariable String ticker) {
        return cached(service.getDividends(market, ticker));
    }
    private static <T> ResponseEntity<T> cached(CacheResult<T> result) {
        var builder = ResponseEntity.ok().header("X-Data-Fresh", Boolean.toString(result.dataFresh()))
                .header("Cache-Control", "no-store");
        if (result.syncedAt() != null) builder.header("X-Data-Synced-At", result.syncedAt() + "Z");
        if (result.warningCode() != null) builder.header("X-Data-Warning", result.warningCode());
        return builder.body(result.data());
    }
}
